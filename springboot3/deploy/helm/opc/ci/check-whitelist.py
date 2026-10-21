#!/usr/bin/env python3
"""
CI check: `springboot3/deploy/nacos/whitelist-opc-invite.yml` MUST be a
**superset** of `springboot3/ruoyi-gateway/src/main/resources/application.yml`'s
`security.ignore.whites:` list.

Background
----------
Spring Cloud resolves the entire `security.ignore.whites:` list from the
highest-priority source (Nacos), and never falls back to jar-internal yaml
on a per-entry basis. Therefore a missing entry in the snippet means that
entry is GONE in production, NOT defaulted back to the local fallback.
This has caused one outage already (see memory `opc-apply-whitelist-union`).

The relationship we want to enforce:

    SNIPPET  ⊇  GATEWAY_LOCAL   (every gateway entry must also be in snippet)

We additionally flag `extra` entries (snippet paths the local fallback does
not have). These are not necessarily bugs — the snippet is the canonical
Nacos-side list and might intentionally add new public endpoints before
the gateway jar is updated — but they force a conscious decision each time.

Both directions are reported, so this script can be wired into CI today,
without a hidden assumption that "extras are silently OK".

Exit codes
----------
0  parse OK on both files AND snippet superset of gateway (no `missing`)
1  parse OK on both files AND there ARE missing entries
2  parse itself failed (zero or invalid YAML on either file)
"""
import os
import sys

os.environ["PYTHONIOENCODING"] = "utf-8"

try:
    import yaml  # PyYAML — sibling diff-envs.py already requires it
    _HAVE_YAML = True
except ImportError:  # pragma: no cover — fallback for bare Python envs
    _HAVE_YAML = False


REPO_ROOT = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "..", "..", "..", "..")
)

SNIPPET_PATH = os.path.join(
    REPO_ROOT, "springboot3", "deploy", "nacos", "whitelist-opc-invite.yml"
)
GATEWAY_PATH = os.path.join(
    REPO_ROOT,
    "springboot3",
    "ruoyi-gateway",
    "src",
    "main",
    "resources",
    "application.yml",
)


# --- ANSI / minimal styling (CI logs strip them; terminals keep them) ---
_USE_COLOR = sys.stdout.isatty()
def _c(code: str, s: str) -> str:
    return f"\033[{code}m{s}\033[0m" if _USE_COLOR else s


def _bold(s: str) -> str:
    return _c("1", s)


def _cyan(s: str) -> str:
    return _c("36", s)


def _red(s: str) -> str:
    return _c("31", s)


def _green(s: str) -> str:
    return _c("32", s)


def _err_print(msg: str) -> None:
    print(msg, file=sys.stderr)


# --- small custom fallback parser (used only if PyYAML is unavailable) ---
# Both files share this simple shape:
#   security:
#     ignore:
#       whites:
#         - /path
#         - /path2
#   ...or possibly inline form:
#       whites: [/a, /b]
# A scan for the `whites:` key under the right ancestor is enough.

def _flatten_strings(value):
    """Yield every string under `value`, descending into lists/dicts."""
    if value is None:
        return
    if isinstance(value, str):
        yield value
        return
    if isinstance(value, (list, tuple)):
        for item in value:
            yield from _flatten_strings(item)
        return
    if isinstance(value, dict):
        for v in value.values():
            yield from _flatten_strings(v)


def _find_whites_via_yaml(path: str):
    with open(path, "r", encoding="utf-8") as fh:
        docs = list(yaml.safe_load_all(fh))
    # application.yml is a single document; whitelist snippet also is.
    for doc in docs:
        if not isinstance(doc, dict):
            continue
        sec = doc.get("security")
        if not isinstance(sec, dict):
            continue
        ign = sec.get("ignore")
        if not isinstance(ign, dict):
            continue
        whites = ign.get("whites")
        if whites is None:
            continue
        # `whites` may be a list of strings, a list with comments-only lines,
        # or a scalar. Flatten anything we find and let the caller normalise.
        return list(_flatten_strings(whites))
    return None


def _find_whites_via_text(path: str):
    """Last-resort parser: scan for the `whites:` key, then collect scalars
    until the next sibling key at the same indent level. This handles both
    `whites: [/a,/b]` and `whites:\\n  - /a\\n  - /b`.
    """
    with open(path, "r", encoding="utf-8") as fh:
        lines = fh.readlines()

    key_indent = None
    for i, line in enumerate(lines):
        stripped = line.rstrip("\n")
        if stripped.lstrip().startswith("#") or not stripped.strip():
            continue
        # Count leading spaces (ignore tabs).
        indent = len(stripped) - len(stripped.lstrip(" "))
        body = stripped.lstrip(" ")
        if key_indent is None:
            if body.startswith("whites:"):
                key_indent = indent
                tail = body[len("whites:"):].strip()
                # Inline form: whites: [/a, /b] or whites: /single
                if tail.startswith("[") and tail.endswith("]"):
                    inner = tail[1:-1].strip()
                    if not inner:
                        return []
                    parts = [p.strip().strip("'\"") for p in inner.split(",")]
                    return [p for p in parts if p]
                if tail and not tail.startswith("#"):
                    return [tail.strip().strip("'\"")]
                # Block form: scan subsequent lines at deeper indent.
                continue
            else:
                continue
        else:
            # We are scanning block-form entries. Stop on a sibling/ancestor.
            if indent <= key_indent and body:
                break
            # Indented line; collect list items `- /path` or plain scalars.
            stripped_body = body.lstrip(" ")
            if stripped_body.startswith("- "):
                entry = stripped_body[2:].strip()
                # strip trailing inline comment
                if " #" in entry:
                    entry = entry.split(" #", 1)[0].rstrip()
                if entry:
                    yield entry
    return _collect_block_form(lines, key_indent)


def _collect_block_form(lines, key_indent):
    """After `whites:` has been matched at `key_indent`, collect all
    list-item scalars at deeper indentation until the indent falls back."""
    out = []
    for line in lines:
        stripped = line.rstrip("\n")
        if not stripped.strip() or stripped.lstrip().startswith("#"):
            continue
        indent = len(stripped) - len(stripped.lstrip(" "))
        body = stripped.lstrip(" ")
        if indent <= key_indent and body and not body.startswith("- "):
            # Back at parent level — done.
            break
        if body.startswith("- "):
            entry = body[2:].strip()
            if " #" in entry:
                entry = entry.split(" #", 1)[0].rstrip()
            if entry:
                out.append(entry)
    return out


def _load_whites(path: str):
    """Return (list_of_strings, parse_method_used)."""
    if _HAVE_YAML:
        try:
            entries = _find_whites_via_yaml(path)
            if entries is None:
                return [], "yaml.safe_load (key not found)"
            return entries, "yaml.safe_load"
        except Exception as exc:  # malformed YAML
            return None, f"yaml.safe_load raised: {exc!r}"
    # Fallback: simple text-based scanner.
    entries = _find_whites_via_text(path)
    if entries is None:
        return [], "custom scanner (key not found)"
    return entries, "custom inline scanner"


def _normalise(entry: str) -> str:
    """Strip whitespace, matching quotes, and a *trailing* slash, but keep
    the leading slash and the case."""
    s = entry.strip()
    # Drop optional matched outer quotes (single or double).
    if len(s) >= 2 and s[0] == s[-1] and s[0] in ("'", '"'):
        s = s[1:-1].strip()
    # Strip trailing slashes only — leading slash is meaningful.
    while len(s) > 1 and s.endswith("/"):
        s = s[:-1]
    return s


def main() -> int:
    print("=== whitelist superset check ===")
    print(f"snippet:  {os.path.relpath(SNIPPET_PATH, REPO_ROOT)}"
          f"  ({'missing' if not os.path.exists(SNIPPET_PATH) else 'present'})")
    print(f"gateway:  {os.path.relpath(GATEWAY_PATH, REPO_ROOT)}"
          f"  ({'missing' if not os.path.exists(GATEWAY_PATH) else 'present'})")
    print()

    if not os.path.exists(SNIPPET_PATH):
        _err_print(f"[FAIL parse] snippet file not found: {SNIPPET_PATH}")
        return 2
    if not os.path.exists(GATEWAY_PATH):
        _err_print(f"[FAIL parse] gateway file not found: {GATEWAY_PATH}")
        return 2

    raw_snippet, snippet_method = _load_whites(SNIPPET_PATH)
    raw_gateway, gateway_method = _load_whites(GATEWAY_PATH)

    snippet_set = {_normalise(e) for e in (raw_snippet or []) if e}
    gateway_set = {_normalise(e) for e in (raw_gateway or []) if e}
    # Drop empties that can sneak in from malformed entries.
    snippet_set.discard("")
    gateway_set.discard("")

    print(f"snippet: parsed {len(snippet_set)} entries via {snippet_method}"
          f"  (raw list len = {len(raw_snippet or []) if raw_snippet is not None else 'N/A'})")
    print(f"gateway: parsed {len(gateway_set)} entries via {gateway_method}"
          f"  (raw list len = {len(raw_gateway or []) if raw_gateway is not None else 'N/A'})")
    print()

    # Parse-failure check: zero parse count OR YAML that errored out.
    parse_failed = False
    if raw_snippet is None or len(snippet_set) == 0:
        _err_print("[FAIL parse] snippet parsed 0 entries.")
        parse_failed = True
    if raw_gateway is None or len(gateway_set) == 0:
        _err_print("[FAIL parse] gateway parsed 0 entries.")
        parse_failed = True
    if parse_failed:
        return 2

    missing = sorted(gateway_set - snippet_set)
    extra = sorted(snippet_set - gateway_set)

    print("missing from snippet (in gateway, NOT in snippet) — must be 0:")
    if missing:
        for m in missing:
            print(f"  - {m}")
    else:
        print(f"  {_green('OK')}")
    print()

    print("extra in snippet (in snippet, NOT in gateway) — review each:")
    if extra:
        for x in extra:
            print(f"  - {x}")
    else:
        print(f"  {_green('OK')}")
    print()

    if missing:
        print(f"verdict: {_red('FAIL missing')}  ({len(missing)} missing, "
              f"{len(extra)} extra)")
        return 1
    print(f"verdict: {_cyan(_bold('PASS'))}  (0 missing, {len(extra)} extra "
          f"to review)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
