import os
import re
import json
import requests
from typing import List, Dict, Any, Set, Tuple
from collections import Counter

# ==============================
# 常量与全局
# ==============================
PROJECT_ID = 8340
API_BASE = f"https://paratranz.cn/api/projects/{PROJECT_ID}"
AUTHORIZATION = os.getenv("AUTHORIZATION")

HEADERS = {
    "Authorization": AUTHORIZATION or "",
    "Content-Type": "application/json",
}

# 统一编译正则
RE_KEY_VALUE = re.compile(r"~\s*(\w+)\s*[\(（](.*?)[\)）]", re.S)
RE_PAREN = re.compile(r"[\(（]([^）\)]*)[）\)]")
RE_COLON_TO_EOL = re.compile(r"[:：]\s*([^\n\r]*)")

# 百分比：允许可选正负号与空格，例如 "+30 %", "-40%", "0%"
RE_PERCENT_SIGNED = re.compile(r"[+-]?\s*\d+\s*%")

# 去掉标签外壳，保留内部文本：<EM4>50</EM4> -> 50
RE_TAGS_STRIP = re.compile(r"</?[^>]+>")

# —— 更严格的分数字词（避免 close-quarter 等误判） ——
RE_FRACTION_PATTERNS = [
    (re.compile(r"\bthree[\s-]?quarters\b", re.I), "75%"),
    (re.compile(r"\btwo[\s-]?thirds\b", re.I), "67%"),
    (re.compile(r"\b(?:a|one)\s+third\b", re.I), "33%"),
    (re.compile(r"\b(?:a|one)\s+quarter\b", re.I), "25%"),
    (re.compile(r"\b(?:a|one)?\s*half\b", re.I), "50%"),  # half / a half / one half
]

# ==============================
# 工具函数
# ==============================
def ensure_auth() -> None:
    if not AUTHORIZATION:
        raise RuntimeError("缺少 AUTHORIZATION 环境变量。请设置后再运行。")

def save_to_json(data: Any, path: str) -> None:
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=4)

def line_count_including_escaped(text: str) -> int:
    return text.count("\n") + text.count("\\n")

def first_line(s: str) -> str:
    return s.replace("\\n", "\n").split("\n", 1)[0]

def merge_json_data(*lists: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    merged: Dict[str, Dict[str, Any]] = {}
    for json_list in lists:
        for item in json_list:
            k = item.get("key")
            if k is None:
                continue
            if k not in merged or item.get("id", -1) > merged[k].get("id", -1):
                merged[k] = item
    return list(merged.values())

# ==============================
# HTTP
# ==============================
def fetch_translation_files(session: requests.Session) -> List[List[Dict[str, Any]]]:
    url = f"{API_BASE}/files"
    r = session.get(url, headers=HEADERS)
    r.raise_for_status()
    result = []
    for item in r.json():
        file_id = item["id"]
        dl = session.get(f"{API_BASE}/files/{file_id}/translation", headers=HEADERS)
        dl.raise_for_status()
        result.append(dl.json())
    return result

def batch_update_stage(session: requests.Session, ids: List[int], stage: int = 2) -> None:
    if not ids:
        return
    payload = {"op": "update", "id": ids, "stage": stage}
    r = session.put(f"{API_BASE}/strings", headers=HEADERS, json=payload)
    r.raise_for_status()
    print(f"成功更新 {len(ids)} 个词条的 stage 为 {stage}。")

# ==============================
# 片段/抽取器
# ==============================
def map_fraction_words_to_percent(text: str) -> List[str]:
    t = text.lower()
    res: List[str] = []
    for pat, pct in RE_FRACTION_PATTERNS:
        if pat.search(t):
            res.append(pct)
    return res

def remove_compared_key_blocks(text: str, compared_keys: Set[str]) -> str:
    out = text
    for k in compared_keys:
        pat = re.compile(rf"~\s*{re.escape(k)}\s*[\(（].*?[\)）]", re.S)
        out = pat.sub("", out)
    return out

def extract_key_pairs(text: str) -> List[Tuple[str, str]]:
    return RE_KEY_VALUE.findall(text)

def strip_tags_keep_inner(s: str) -> str:
    """去掉 HTML 样式标签外壳，保留内部文本（避免 <EM4> 之类干扰）"""
    return RE_TAGS_STRIP.sub("", s)

def extract_inspected_parts(text: str, compared_keys: Set[str]) -> Tuple[List[str], List[str]]:
    """
    先移除 ~key(...)（仅限已比对key），再抽取：
    - 冒号后的片段
    - 括号内的片段
    并把标签外壳去掉（保留内部文本）
    """
    cleaned = remove_compared_key_blocks(text, compared_keys) if compared_keys else text
    colon_parts = RE_COLON_TO_EOL.findall(cleaned)
    paren_parts = RE_PAREN.findall(cleaned)
    colon_parts = [strip_tags_keep_inner(p) for p in colon_parts]
    paren_parts = [strip_tags_keep_inner(p) for p in paren_parts]
    return colon_parts, paren_parts

# —— 百分比提取（保留符号，去内部空格） ——
def extract_percentages_normalized(parts: List[str]) -> List[str]:
    """
    提取百分比，保留正负号；去掉内部空格：
    "+40 %", "+ 40%"  → "+40%"
    "-40 %", "- 40%"  → "-40%"
    "40 %"            → "40%"
    """
    res: List[str] = []
    for s in parts:
        for m in RE_PERCENT_SIGNED.findall(s):
            norm = re.sub(r"\s+", "", m)  # 去空格但保留符号
            res.append(norm)              # e.g. "+40%", "-40%", "40%"
    return res

def strip_percentages(text: str) -> str:
    """去掉百分比（用于数字提取避免 '25%' 再计作 25）"""
    return RE_PERCENT_SIGNED.sub("", text)

def extract_numbers_from_colon(colon_parts: List[str]) -> List[int]:
    """
    只在“冒号后紧跟数字”的情况下提取（允许空格；标签壳已剥离）：
    - ":   50-55 SCU" -> 取 50
    - 如果开头不是数字（如先是文字/符号），不取任何数字
    """
    nums: List[int] = []
    for s in colon_parts:
        s_clean = strip_percentages(s)
        m = re.match(r"\s*(\d+)", s_clean)
        if m:
            nums.append(int(m.group(1)))
    return nums

def extract_numbers_from_paren(paren_parts: List[str]) -> List[int]:
    """
    括号段：直接识别全部“数字段”（\\d+），不再要求独立数字。
    例如：8x、8倍、UE5、v2.1 都会提取出 8、5、2、1。
    先去掉百分比，避免 '25%' 再被算作 25。
    """
    nums: List[int] = []
    for s in paren_parts:
        s_clean = strip_percentages(s)
        nums.extend(int(n) for n in re.findall(r"\d+", s_clean))
    return nums

# ==============================
# 业务校验
# ==============================
def check_mission_consistency(data: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    inconsistencies: List[Dict[str, Any]] = []

    for entry in data:
        key = entry.get("key", "") or ""
        original_raw = entry.get("original", "") or ""
        translation_raw = entry.get("translation", "") or ""
        if not translation_raw:
            continue

        # ~key(...) 配对（所有 key 都检查）
        orig_pairs = extract_key_pairs(original_raw)
        trans_pairs = extract_key_pairs(translation_raw)
        orig_mis = [m for m in orig_pairs if m not in trans_pairs]
        trans_mis = [m for m in trans_pairs if m not in orig_pairs]
        compared_keys: Set[str] = set([k for k, _ in orig_pairs] + [k for k, _ in trans_pairs])

        # 受检区域
        orig_colon, orig_paren = extract_inspected_parts(original_raw, compared_keys)
        trans_colon, trans_paren = extract_inspected_parts(translation_raw, compared_keys)

        # —— 仅 item_* 做“数字 & 百分比”检查 ——
        do_numeric_checks = key.startswith("item_")

        # 百分比（译文允许超集；保留正负号；兼容空格）
        if do_numeric_checks:
            orig_pct_explicit = extract_percentages_normalized(orig_colon + orig_paren)

            # 译文先看受检区；若原文有%而受检区没抓到，则兜底全句扫描
            trans_pct = extract_percentages_normalized(trans_colon + trans_paren)
            if orig_pct_explicit and not trans_pct:
                whole_trans = strip_tags_keep_inner(translation_raw)
                trans_pct = extract_percentages_normalized([whole_trans])

            # 分数字词映射（原文受检区）
            mapped_from_fraction = []
            for s in orig_colon + orig_paren:
                mapped_from_fraction += map_fraction_words_to_percent(s)

            # 忽略 ±100%
            def _drop_100(lst: List[str]) -> List[str]:
                return [p for p in lst if p.lstrip("+-") != "100%"]

            req = _drop_100(orig_pct_explicit) + mapped_from_fraction
            got = _drop_100(trans_pct)

            # 译文多出来也可以：Counter(req) ⊆ Counter(got)
            def _is_multiset_subset(a: List[str], b: List[str]) -> bool:
                ca, cb = Counter(a), Counter(b)
                return all(cb[k] >= v for k, v in ca.items())

            perc_mis = not _is_multiset_subset(req, got)
        else:
            perc_mis = False
            orig_pct_explicit, trans_pct, mapped_from_fraction = [], [], []

        # 数字（冒号与括号分开；括号仅当“原文括号里存在数字”时才比较，避免结构性翻译差异误报）
        if do_numeric_checks:
            orig_colon_nums = extract_numbers_from_colon(orig_colon)
            trans_colon_nums = extract_numbers_from_colon(trans_colon)

            orig_paren_nums = extract_numbers_from_paren(orig_paren)
            trans_paren_nums = extract_numbers_from_paren(trans_paren)

            colon_mis = Counter(orig_colon_nums) != Counter(trans_colon_nums)
            paren_mis = Counter(orig_paren_nums) != Counter(trans_paren_nums) if orig_paren_nums else False
            num_mis = colon_mis or paren_mis
        else:
            orig_colon_nums = trans_colon_nums = []
            orig_paren_nums = trans_paren_nums = []
            num_mis = False

        # 换行（所有 key 仍可检查）
        nl_orig = line_count_including_escaped(original_raw)
        nl_trans = line_count_including_escaped(translation_raw)
        nl_mis = nl_orig != nl_trans

        if orig_mis or trans_mis or num_mis or perc_mis or nl_mis:
            perc_report = {}
            if perc_mis:
                perc_report = {
                    "original_percentages_explicit": orig_pct_explicit,
                    "translation_percentages": trans_pct,
                }
                if mapped_from_fraction:
                    perc_report["original_percentages_mapped_from_fraction_words"] = mapped_from_fraction

            number_report = {}
            if num_mis:
                number_report = {
                    "colon": {
                        "original": orig_colon_nums,
                        "translation": trans_colon_nums,
                    },
                }
                if orig_paren_nums:
                    number_report["paren"] = {
                        "original": orig_paren_nums,
                        "translation": trans_paren_nums,
                    }

            inconsistencies.append({
                "key": entry.get("key"),
                "id": entry.get("id"),
                "original": original_raw,
                "translation": translation_raw,
                "original_mismatches": orig_mis,
                "translation_mismatches": trans_mis,
                "number_mismatches": number_report,
                "percentage_mismatches": perc_report,
                "newline_mismatch": {
                    "original_newlines": nl_orig,
                    "translation_newlines": nl_trans,
                } if nl_mis else {},
            })

    return inconsistencies

def check_item_types(data: List[Dict[str, Any]]) -> List[str]:
    item_type_map: Dict[str, Set[str]] = {}
    issues: List[str] = []
    for entry in data:
        tr = entry.get("translation") or ""
        or_ = entry.get("original") or ""
        if not tr:
            continue
        if "Item Type: " in or_ and "物品类型：" in tr:
            cn_type = first_line(tr.split("物品类型：", 1)[1])
            en_type = first_line(or_.split("Item Type: ", 1)[1])
            item_type_map.setdefault(en_type, set()).add(cn_type)

    for en_type, cn_types in item_type_map.items():
        if len(cn_types) > 1:
            issues.append(f"English type '{en_type}' corresponds to multiple Chinese types: {sorted(cn_types)}")

    missing_keys = [
        entry.get("key")
        for entry in data
        if "Item Type: " in (entry.get("original") or "")
        and "物品类型：" not in (entry.get("translation") or "")
    ]
    if missing_keys:
        issues.append("Keys of original texts with 'Item Type: ' but missing '物品类型：' in translation:")
        issues.extend([str(k) for k in missing_keys])

    return issues

# ==============================
# 主流程
# ==============================
def main() -> None:
    ensure_auth()
    with requests.Session() as s:
        # 1) 获取并合并
        file_data = fetch_translation_files(s)
        merged_data = merge_json_data(*file_data)
        save_to_json(merged_data, "merged_data.json")

        # 2) 一致性检查
        inconsistencies = check_mission_consistency(merged_data)
        if inconsistencies:
            filtered = [
                inc for inc in inconsistencies
                if inc.get("original_mismatches")
                or inc.get("translation_mismatches")
                or inc.get("number_mismatches")
                or inc.get("percentage_mismatches")
                or inc.get("newline_mismatch")
            ]
            save_to_json(filtered, "inconsistencies.json")
            print(f"发现 {len(filtered)} 个格式不一致项，详情见 inconsistencies.json")
            failed_ids = [inc["id"] for inc in filtered if inc.get("id") is not None]
            batch_update_stage(s, failed_ids, stage=2)
        else:
            print("所有格式内容一致，无不一致项。")
            # 3) 物品类型检查（保持原有策略）
            item_type_issues = check_item_types(merged_data)
            if item_type_issues:
                save_to_json(item_type_issues, "inconsistencies.json")
                print("检测到物品类型不一致，详情见 inconsistencies.json")
            else:
                print("所有物品类型一致，无不一致项。")

if __name__ == "__main__":
    main()
