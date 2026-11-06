#!/usr/bin/env python3
"""日报正文里的数字, 必须来自 kpi_json —— 模型不许编。

这是 CLAUDE.md 第二节那条规矩的落地: prompt 里写了「不得出现给定数据里没有的数字」,
那是**请求**; 这里是**断言** —— 请求可以不被执行, 断言会红。

断三件事:
  26.14  正文里每一个「N 元」/「N 笔」, 都得能在 kpi_json 里找到(数值相等)
  26.15  昨日没有流水, 正文必须明说; 且不许把「没有流水」写成「运营平稳」
  26.15b 昨日明明有流水, 正文不许说「无资金流水」

用法(给 health-check.sh 调):
    report_body_truth.py <summary_file> <kpi_json_file>
退出码: 0 = 通过, 1 = 有违规, 2 = 用法/输入不对
输出: 每行一条结论。OK|<说明> 或 FAIL|<代码>|<说明>

单测:
    cd springboot3/deploy/scripts && python3 -m unittest discover -s tests -v
"""

import json
import re
import sys

# 「N 元」/「N 万元」—— 正文里所有带货币单位的数字。
# 「万」要单独认: 「1.9462 万元」= 19462 元(对得上),「1.9 万元」= 19000(编的)。
AMOUNT_RE = re.compile(r"([0-9]+(?:\.[0-9]+)?)\s*(万)?\s*元")
# 「N 笔」—— 正文里对数据量的说法。
COUNT_RE = re.compile(r"([0-9]+)\s*笔")

# 「昨日没有流水」的几种说法(去空格后匹配)。
NO_FLOW_WORDS = ("无资金流水", "没有资金流水", "无流水", "没有流水", "零流水", "无资金交易")

# 「平稳/稳定」前面有这些词 = **否定**的说法, 不能算谎报。
# 「在昨日无资金流水的情况下, 不宜直接判断资金运营平稳」是实话, 不是谎话。
NEGATIONS = (
    "不宜", "不得", "不能", "无法", "不应", "不可", "不足以", "不代表",
    "谨慎", "暂不", "尚不", "未", "没法",
)

STABLE_WORDS = ("平稳", "稳定")


def _to_float(value):
    try:
        return round(float(value), 2)
    except (TypeError, ValueError):
        return None


def _collect_amounts(kpi):
    """kpi_json 里所有「真金额」的集合 —— 正文里的金额必须落在这个集合里。"""
    allowed = set()
    for source in ("query_bank_flow", "query_voucher"):
        data = kpi.get(source) or {}
        for key in ("totalIn", "totalOut", "net", "yesterdayIn", "yesterdayOut"):
            value = _to_float(data.get(key))
            if value is not None:
                allowed.add(value)
        for item in data.get("items") or []:
            for key in ("amount", "totalDebit", "totalCredit"):
                value = _to_float(item.get(key))
                if value is not None:
                    allowed.add(value)
    return allowed


def _collect_counts(kpi):
    """kpi_json 里所有「真笔数」的集合 —— 正文里的笔数必须落在这个集合里。"""
    bank = kpi.get("query_bank_flow") or {}
    voucher = kpi.get("query_voucher") or {}
    allowed = set()
    for data in (bank, voucher):
        count = data.get("count")
        if isinstance(count, int):
            allowed.add(count)
    if isinstance(bank.get("count"), int) and isinstance(voucher.get("count"), int):
        allowed.add(bank["count"] + voucher["count"])
    # 昨日 0 笔时, 正文写「昨日 0 笔」是实话。
    if _yesterday_count(bank) == 0 and _yesterday_count(voucher) == 0:
        allowed.add(0)
    return allowed


def _yesterday_count(data):
    count = data.get("yesterdayCount")
    return count if isinstance(count, int) else -1


def _sentences(text):
    return [s for s in re.split(r"[。;；\n]+", text) if s.strip()]


def check_report_body(summary, kpi):
    """断一次日报正文。

    返回 {"violations": [...], "groups": {...}, "checked": {...}}。
    violations 为空 = 通过。

    groups 分两组, 对应两条断言:
      FABRICATION  正文里的数字(金额/笔数)必须来自 kpi_json   → 26.14
      NO_FLOW      昨日有没有流水, 正文必须照实说              → 26.15
    """
    violations = []
    checked = {"amounts": 0, "counts": 0}
    groups = {
        "FABRICATION": {"ok": True, "detail": "", "violations": []},
        "NO_FLOW": {"ok": True, "detail": "", "violations": []},
    }

    def add(group, code, detail, **extra):
        item = {"code": code, "detail": detail}
        item.update(extra)
        violations.append(item)
        groups[group]["ok"] = False
        groups[group]["violations"].append(item)

    if summary is None or not summary.strip():
        add("FABRICATION", "EMPTY_BODY",
            "日报正文是空的 —— 26.13 断的是「正文非空」, 这里断的是「正文不是空壳」")
        groups["NO_FLOW"]["detail"] = "正文是空的, 没法断「有没有照实说昨日有无流水」"
        groups["NO_FLOW"]["ok"] = False
        groups["NO_FLOW"]["violations"].append({
            "code": "EMPTY_BODY", "detail": groups["NO_FLOW"]["detail"]})
        return {"violations": violations, "groups": groups, "checked": checked}

    bank = kpi.get("query_bank_flow") or {}
    voucher = kpi.get("query_voucher") or {}
    y_bank = _yesterday_count(bank)
    y_voucher = _yesterday_count(voucher)

    # --- 26.14 正文里的金额不许编 ---
    allowed_amounts = _collect_amounts(kpi)
    flat = summary.replace(",", "")
    for match in AMOUNT_RE.finditer(flat):
        # 这里不能用 _to_float: 它会把 1.9462 四舍五入成 1.95,
        # 再乘 10000 就变成 19500 —— 「1.9462 万元」会被误判成编的数。
        value = float(match.group(1))
        if match.group(2):  # 万元
            value = value * 10000
        value = round(value, 2)
        checked["amounts"] += 1
        if value not in allowed_amounts:
            add("FABRICATION", "FABRICATED_AMOUNT",
                "正文里的「{}」({}) 在 kpi_json 里没有对应金额 —— 模型编了数"
                .format(match.group(0).strip(), value),
                value=value, text=match.group(0).strip())

    # --- 26.14 正文里的笔数不许编 ---
    allowed_counts = _collect_counts(kpi)
    for match in COUNT_RE.finditer(flat):
        value = int(match.group(1))
        checked["counts"] += 1
        if value not in allowed_counts:
            add("FABRICATION", "FABRICATED_COUNT",
                "正文里的「{}笔」在 kpi_json 里没有对应笔数 —— 模型编了数"
                .format(value),
                value=value, text=match.group(0).strip())

    if groups["FABRICATION"]["ok"]:
        groups["FABRICATION"]["detail"] = "正文里的 {} 个金额 / {} 个笔数都能在 kpi_json 里对上".format(
            checked["amounts"], checked["counts"])

    # --- 26.15 昨日有没有流水, 正文必须照实说 ---
    no_said = any(word in flat.replace(" ", "") for word in NO_FLOW_WORDS)
    if y_bank == 0 and y_voucher == 0:
        if not no_said:
            add("NO_FLOW", "MISSING_NO_FLOW",
                "昨日一笔流水都没有, 正文却没说「无资金流水」—— 把没有数据说成了有结论")
        # 没有流水, 不许说「运营平稳」
        for sentence in _sentences(flat):
            if any(word in sentence for word in STABLE_WORDS) \
                    and not any(neg in sentence for neg in NEGATIONS):
                add("NO_FLOW", "FALSE_STABLE",
                    "昨日没有流水, 正文却写「{}」—— 把没有数据说成了运营平稳"
                    .format(sentence.strip()[:40]),
                    text=sentence.strip()[:120])
        if groups["NO_FLOW"]["ok"]:
            groups["NO_FLOW"]["detail"] = "昨日确实没有流水, 正文照实说了"
    elif y_bank > 0 and no_said:
        add("NO_FLOW", "FALSE_NO_FLOW",
            "昨日有 {} 笔流水, 正文却说「无资金流水」—— 谎报没有流水".format(y_bank))
    elif groups["NO_FLOW"]["ok"]:
        groups["NO_FLOW"]["detail"] = "昨日有 {} 笔流水, 正文没有谎报没有流水".format(y_bank)

    return {"violations": violations, "groups": groups, "checked": checked}


def main(argv):
    if len(argv) != 3:
        sys.stderr.write("用法: report_body_truth.py <summary_file> <kpi_json_file>\n")
        return 2
    try:
        with open(argv[1], encoding="utf-8") as handle:
            summary = handle.read()
        with open(argv[2], encoding="utf-8") as handle:
            kpi = json.load(handle)
    except (OSError, ValueError) as exc:
        sys.stderr.write("读不了输入: {}\n".format(exc))
        return 2

    result = check_report_body(summary, kpi)
    failed = False
    for group, verdict in result["groups"].items():
        if verdict["ok"]:
            print("GROUP|{}|OK|{}".format(group, verdict["detail"]))
            continue
        failed = True
        for violation in verdict["violations"]:
            print("GROUP|{}|FAIL|{}|{}".format(
                group, violation["code"], violation["detail"]))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
