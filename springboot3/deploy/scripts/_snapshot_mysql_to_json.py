#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Helper: build snapshot-info-<date>.json from a TSV dump of MySQL table rows."""
import json
import sys
from pathlib import Path

if len(sys.argv) != 7:
    print("Usage: _snapshot_mysql_to_json.py <out_json> <date> <ts> <db> <table_count> <tsv_data_file>",
          file=sys.stderr)
    sys.exit(2)

out_json = sys.argv[1]
date_s = sys.argv[2]
ts = sys.argv[3]
db = sys.argv[4]
table_count = int(sys.argv[5])
tsv_path = Path(sys.argv[6])

tables = {}
if tsv_path.exists() and tsv_path.stat().st_size > 0:
    for line in tsv_path.read_text(encoding="utf-8").splitlines():
        parts = line.split("\t")
        if len(parts) >= 2:
            try:
                rows = int(parts[1])
            except ValueError:
                rows = 0
            tables[parts[0]] = rows

doc = {
    "snapshot_date": date_s,
    "snapshot_ts":   ts,
    "database":      db,
    "table_count":   table_count,
    "tables":        tables,
    "crm_tables":          {k: v for k, v in tables.items() if k.startswith("opc_crm_")},
    "notification_tables": {k: v for k, v in tables.items() if k.startswith("opc_notification")},
}
Path(out_json).write_text(json.dumps(doc, indent=2, ensure_ascii=False), encoding="utf-8")
print(f"  -> {out_json}")