#!/usr/bin/env python3
"""report_body_truth 的单元测试。

跑法:
    cd springboot3/deploy/scripts && python3 -m unittest discover -s tests -v

为什么要单测: 这条断言的价值全在「它会不会红」。
一条永远绿的断言等于没有断言 —— 所以每个「应该报红」的场景都要有一个用例,
而且必须**亲眼看着它红**才算数(见 CLAUDE.md 第二节)。

对应线上: 26.14(日报正文里的数字不许编) / 26.15(昨日无流水必须明说)。
"""

import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from report_body_truth import check_report_body  # noqa: E402


def kpi(yesterday_count=0, bank_count=6, voucher_count=5):
    """一份和线上一模一样形状的 kpi_json。

    线上那 6 笔银行流水是 51892 / 32430 / 19462 五笔凭证是 35400 / 29750 / 5650。
    """
    return {
        "query_bank_flow": {
            "source": "opc_finance_bank_flow",
            "companyId": 2,
            "fromDate": "2026-10-07",
            "toDate": "2026-11-04",
            "count": bank_count,
            "totalIn": 51892.0,
            "totalOut": 32430.0,
            "net": 19462.0,
            "yesterdayIn": 0,
            "yesterdayOut": 0,
            "yesterdayCount": yesterday_count,
            "items": [
                {"amount": 13200.0, "direction": "OUT", "flowCode": "BF2026100101"},
                {"amount": 38450.0, "direction": "IN", "flowCode": "BF2026100102"},
                {"amount": 12150.0, "direction": "OUT", "flowCode": "BF2026100103"},
                {"amount": 9860.0, "direction": "IN", "flowCode": "BF2026100104"},
                {"amount": 7080.0, "direction": "OUT", "flowCode": "BF2026100105"},
                {"amount": 3582.0, "direction": "IN", "flowCode": "BF2026100106"},
            ],
        },
        "query_voucher": {
            "source": "opc_finance_voucher",
            "companyId": 2,
            "fromDate": "2026-10-07",
            "toDate": "2026-11-04",
            "count": voucher_count,
            "totalIn": 35400.0,
            "totalOut": 29750.0,
            "net": 5650.0,
            "yesterdayIn": 0,
            "yesterdayOut": 0,
            "yesterdayCount": yesterday_count,
            "items": [
                {"totalDebit": 13200.0, "totalCredit": 0.0, "voucherCode": "VCH20261001"},
                {"totalDebit": 12150.0, "totalCredit": 0.0, "voucherCode": "VCH20261003"},
                {"totalDebit": 2975.0, "totalCredit": 0.0, "voucherCode": "VCH20261004"},
                {"totalDebit": 2600.0, "totalCredit": 0.0, "voucherCode": "VCH20261004"},
                {"totalDebit": 1800.0, "totalCredit": 0.0, "voucherCode": "VCH20261005"},
            ],
        },
    }


class TestAmounts(unittest.TestCase):
    """26.14: 正文里的每一个「N 元」都必须来自 kpi_json。"""

    def test_amounts_all_from_kpi_passes(self):
        summary = (
            "## 【关键数字】\n\n"
            "- 银行流水共 6 笔：流入 51892.00 元，流出 32430.00 元，净额 19462.00 元。\n"
            "- 财务凭证共 5 笔：收入 35400.00 元，支出 29750.00 元，净额 5650.00 元。\n"
            "- 单笔 BF2026100102 金额 38450.00 元。\n"
        )
        res = check_report_body(summary, kpi(yesterday_count=3))
        self.assertEqual([], res["violations"], "全部金额都能对上, 不该有违规")

    def test_fabricated_amount_fails(self):
        summary = "- 流入 999999.00 元。\n"
        res = check_report_body(summary, kpi(yesterday_count=3))
        codes = [v["code"] for v in res["violations"]]
        self.assertIn("FABRICATED_AMOUNT", codes)
        self.assertIn("999999.00", json.dumps(res["violations"], ensure_ascii=False))

    def test_wan_unit_amount_must_match_after_scaling(self):
        """「1.9462 万元」= 19462 元 —— 允许;「1.9 万元」= 19000 —— 不允许。"""
        ok = check_report_body("- 净额 1.9462 万元。\n", kpi(yesterday_count=3))
        self.assertEqual([], ok["violations"], "1.9462 万元 就是 19462 元")

        bad = check_report_body("- 净额 1.9 万元。\n", kpi(yesterday_count=3))
        codes = [v["code"] for v in bad["violations"]]
        self.assertIn("FABRICATED_AMOUNT", codes, "1.9 万元 = 19000, 库里没有这个数")

    def test_thousand_separator_amount_matches(self):
        res = check_report_body("- 流入 51,892.00 元。\n", kpi(yesterday_count=3))
        self.assertEqual([], res["violations"], "带千分位写法也是同一个数")

    def test_count_must_match(self):
        ok = check_report_body("- 银行流水共 6 笔，凭证共 5 笔。\n", kpi(yesterday_count=3))
        self.assertEqual([], ok["violations"])

        bad = check_report_body("- 银行流水共 7 笔。\n", kpi(yesterday_count=3))
        codes = [v["code"] for v in bad["violations"]]
        self.assertIn("FABRICATED_COUNT", codes)


class TestYesterdayFlow(unittest.TestCase):
    """26.15: 昨日有没有流水, 正文必须照实说。"""

    NO_FLOW_WORDS = ("无资金流水", "没有资金流水", "无流水", "没有流水", "零流水")

    def test_no_flow_must_be_stated(self):
        summary = "- 银行流水共 6 笔：流入 51892.00 元。\n- 营收 0 元，运营平稳。\n"
        res = check_report_body(summary, kpi(yesterday_count=0))
        codes = [v["code"] for v in res["violations"]]
        self.assertIn("MISSING_NO_FLOW", codes, "昨日 0 笔却没说无流水")

    def test_no_flow_stated_passes(self):
        summary = (
            "- 银行流水共 6 笔：流入 51892.00 元。\n"
            "- **昨日无资金流水**；银行流水与凭证昨日记录数均为 0。\n"
        )
        res = check_report_body(summary, kpi(yesterday_count=0))
        self.assertEqual([], res["violations"])

    def test_no_flow_but_called_stable_fails(self):
        """「不宜直接判断资金运营平稳」是**正确**的写法, 不能误伤。"""
        good = (
            "- **昨日无资金流水**；\n"
            "- 补查昨日渠道结算；在昨日无资金流水的情况下，不宜直接判断资金运营平稳。\n"
        )
        res = check_report_body(good, kpi(yesterday_count=0))
        self.assertEqual([], res["violations"], "带否定词的「平稳」不是谎报")

        bad = (
            "- **昨日无资金流水**；\n"
            "- 营收 0 元，运营平稳。\n"
        )
        res2 = check_report_body(bad, kpi(yesterday_count=0))
        codes = [v["code"] for v in res2["violations"]]
        self.assertIn("FALSE_STABLE", codes, "把没有流水说成运营平稳")

    def test_cannot_claim_no_flow_when_there_is_flow(self):
        summary = "- **昨日无资金流水**。\n- 流入 51892.00 元。\n"
        res = check_report_body(summary, kpi(yesterday_count=3))
        codes = [v["code"] for v in res["violations"]]
        self.assertIn("FALSE_NO_FLOW", codes, "昨日明明有流水却说无流水")

    def test_zero_amount_allowed_when_no_flow(self):
        """昨日 0 笔时正文写「0 元」是**实话**, 不能报错。"""
        summary = "- **昨日无资金流水**，昨日流入 0 元，流出 0 元。\n"
        res = check_report_body(summary, kpi(yesterday_count=0))
        self.assertEqual([], res["violations"])

    def test_empty_summary_is_empty_not_ok(self):
        """正文是空的 → 26.13 已经管了, 这里也得说清楚, 不能当成「没发现问题」。"""
        res = check_report_body("", kpi())
        self.assertTrue(res["violations"], "空正文不能判通过")


if __name__ == "__main__":
    unittest.main()
