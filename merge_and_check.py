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

# 统一编译正则（避免重复）
RE_KEY_VALUE = re.compile(r"~\s*(\w+)\s*[\(（](.*?)[\)）]", re.S)
RE_PAREN = re.compile(r"[\(（]([^）\)]*)[）\)]")
RE_COLON_TO_EOL = re.compile(r"[:：]\s*([^\n\r]*)")
RE_PERCENT = re.compile(r"\d+%")
RE_DIGITS = re.compile(r"\d+")
RE_FRACTION_WORDS = [
    (re.compile(r"three[\s-]?quarters", re.I), "75%"),
    (re.compile(r"two[\s-]?thirds", re.I), "67%"),
    (re.compile(r"\bone[\s-]?third\b", re.I), "33%"),
    # 独立的 "third"（避免和 "one third" 重复）
    (re.compile(r"\bthird\b", re.I), "33%"),
    (re.compile(r"\bquarter\b", re.I), "25%"),
    (re.compile(r"\bhalf\b", re.I), "50%"),
]

# ==============================
# 工具函数（通用）
# ==============================
def ensure_auth() -> None:
    if not AUTHORIZATION:
        raise RuntimeError("缺少 AUTHORIZATION 环境变量。请设置后再运行。")

def save_to_json(data: Any, path: str) -> None:
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=4)

def line_count_including_escaped(text: str) -> int:
    """真实换行 + 转义 \\n 的合计。"""
    return text.count("\n") + text.count("\\n")

def first_line(s: str) -> str:
    """统一把 '\\n' 视作换行符，然后取首行。"""
    return s.replace("\\n", "\n").split("\n", 1)[0]

def merge_json_data(*lists: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """合并多个 JSON 列表，保留每个 key 下 id 最大的数据。"""
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
# HTTP 层
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
# 文本受检区与抽取器（消除重复）
# ==============================
def map_fraction_words_to_percent(text: str) -> List[str]:
    t = text.lower()
    res: List[str] = []
    for pat, pct in RE_FRACTION_WORDS:
        if pat.search(t):
            res.append(pct)
    return res

def remove_compared_key_blocks(text: str, compared_keys: Set[str]) -> str:
    """只移除已参与 ~key(...) 比对的 key 的块，避免误删普通括号。"""
    out = text
    for k in compared_keys:
        # ~ key (...) 多行非贪婪
        pat = re.compile(rf"~\s*{re.escape(k)}\s*[\(（].*?[\)）]", re.S)
        out = pat.sub("", out)
    return out

def extract_key_pairs(text: str) -> List[Tuple[str, str]]:
    return RE_KEY_VALUE.findall(text)

def extract_inspected_parts(text: str, compared_keys: Set[str]) -> Tuple[List[str], List[str]]:
    """
    先移除 ~key(...)（仅限已比对的 key），然后抽取：
    - 冒号后的片段
    - 括号内的片段
    """
    cleaned = remove_compared_key_blocks(text, compared_keys) if compared_keys else text
    colon_parts = RE_COLON_TO_EOL.findall(cleaned)
    paren_parts = RE_PAREN.findall(cleaned)
    return colon_parts, paren_parts

def extract_percentages(parts: List[str]) -> List[str]:
    res: List[str] = []
    for s in parts:
        res += RE_PERCENT.findall(s)
    return res

def extract_mapped_percents_from_fraction_words(parts: List[str]) -> List[str]:
    res: List[str] = []
    for s in parts:
        res += map_fraction_words_to_percent(s)
    return res

def extract_numbers(parts: List[str]) -> List[int]:
    """仅提取纯数字（剔除百分比中的数字）。"""
    nums: List[int] = []
    for s in parts:
        s_nopct = RE_PERCENT.sub("", s)
        nums += [int(x) for x in RE_DIGITS.findall(s_nopct)]
    return nums

# ==============================
# 业务校验
# ==============================
def check_mission_consistency(data: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    inconsistencies: List[Dict[str, Any]] = []

    for entry in data:
        original_raw = entry.get("original", "") or ""
        translation_raw = entry.get("translation", "") or ""
        if not translation_raw:
            continue

        # ~key(...) 配对
        orig_pairs = extract_key_pairs(original_raw)
        trans_pairs = extract_key_pairs(translation_raw)
        orig_mis = [m for m in orig_pairs if m not in trans_pairs]
        trans_mis = [m for m in trans_pairs if m not in orig_pairs]
        compared_keys: Set[str] = set([k for k, _ in orig_pairs] + [k for k, _ in trans_pairs])

        # 抽取受检区域
        orig_colon, orig_paren = extract_inspected_parts(original_raw, compared_keys)
        trans_colon, trans_paren = extract_inspected_parts(translation_raw, compared_keys)

        # 百分比
        orig_pct_explicit = extract_percentages(orig_colon + orig_paren)
        trans_pct = extract_percentages(trans_colon + trans_paren)

        mapped_from_fraction = extract_mapped_percents_from_fraction_words(orig_colon + orig_paren)

        # 忽略 100%
        filt_orig_pct = [p for p in orig_pct_explicit if p != "100%"]
        filt_trans_pct = [p for p in trans_pct if p != "100%"]

        orig_pct_for_compare = filt_orig_pct + mapped_from_fraction

        # 仅有分数字词且译文无显式百分比 → 视为兼容
        if mapped_from_fraction and not filt_orig_pct and not filt_trans_pct:
            perc_mis = False
        else:
            perc_mis = Counter(orig_pct_for_compare) != Counter(filt_trans_pct)

        # 数字（多重集合）
        orig_nums = extract_numbers(orig_colon + orig_paren)
        trans_nums = extract_numbers(trans_colon + trans_paren)
        num_mis = Counter(orig_nums) != Counter(trans_nums)

        # 换行
        nl_orig = line_count_including_escaped(original_raw)
        nl_trans = line_count_including_escaped(translation_raw)
        nl_mis = nl_orig != nl_trans

        if orig_mis or trans_mis or num_mis or perc_mis or nl_mis:
            perc_report = {
                "original_percentages_explicit": orig_pct_explicit,
                "translation_percentages": trans_pct,
            }
            if mapped_from_fraction:
                perc_report["original_percentages_mapped_from_fraction_words"] = mapped_from_fraction

            inconsistencies.append({
                "key": entry.get("key"),
                "id": entry.get("id"),
                "original": original_raw,
                "translation": translation_raw,
                "original_mismatches": orig_mis,
                "translation_mismatches": trans_mis,
                "number_mismatches": {
                    "original_numbers": orig_nums,
                    "translation_numbers": trans_nums,
                } if num_mis else {},
                "percentage_mismatches": perc_report if perc_mis else {},
                "newline_mismatch": {
                    "original_newlines": nl_orig,
                    "translation_newlines": nl_trans,
                } if nl_mis else {},
            })

    return inconsistencies

def check_item_types(data: List[Dict[str, Any]]) -> List[str]:
    """检查物品类型一致性（抽取首行，避免 '\\n'/实际换行差异）。"""
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
        # 1. 获取并合并数据
        file_data = fetch_translation_files(s)
        merged_data = merge_json_data(*file_data)
        save_to_json(merged_data, "merged_data.json")

        # 2. 检查一致性
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
            # 3. 检查物品类型（保持与原逻辑一致：仅在无格式不一致时执行）
            item_type_issues = check_item_types(merged_data)
            if item_type_issues:
                save_to_json(item_type_issues, "inconsistencies.json")
                print("检测到物品类型不一致，详情见 inconsistencies.json")
            else:
                print("所有物品类型一致，无不一致项。")

if __name__ == "__main__":
    main()
