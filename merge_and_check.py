import requests
import json
import re
import os
from typing import List, Dict, Any, Set
from collections import Counter

# ==============================
# 常量定义
# ==============================
PROJECT_ID = 8340
API_BASE = f"https://paratranz.cn/api/projects/{PROJECT_ID}"
AUTHORIZATION = os.getenv('AUTHORIZATION')

HEADERS = {
    'Authorization': AUTHORIZATION,
    'Content-Type': 'application/json'
}

# ==============================
# 工具函数
# ==============================
def merge_json_data(*lists: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """合并多个 JSON 列表，保留每个 key 下 id 最大的数据。"""
    merged = {}
    for json_list in lists:
        for item in json_list:
            key = item['key']
            if key not in merged or item['id'] > merged[key]['id']:
                merged[key] = item
    return list(merged.values())

def save_to_json(data: Any, path: str) -> None:
    """保存数据到 JSON 文件。"""
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=4)

def fetch_translation_files() -> List[List[Dict[str, Any]]]:
    """获取所有翻译文件的内容。"""
    url = f"{API_BASE}/files"
    resp = requests.get(url, headers=HEADERS)
    resp.raise_for_status()
    result = []
    for item in resp.json():
        download_url = f"{API_BASE}/files/{item['id']}/translation"
        file_resp = requests.get(download_url, headers=HEADERS)
        file_resp.raise_for_status()
        result.append(file_resp.json())
    return result

def _map_fraction_words_to_percent(text: str) -> List[str]:
    """
    将英文中的常见分数字词映射为百分比字符串列表，例如：
    - "quarter" -> "25%"
    - "half" -> "50%"
    - "three quarters" -> "75%"
    - "third"/"one third" -> "33%"
    - "two thirds" -> "67%"
    返回映射出的百分比字符串（可能为空）。
    """
    t = text.lower()
    res = []
    if re.search(r"three[\s-]?quarters", t):
        res.append("75%")
    if re.search(r"two[\s-]?thirds", t):
        res.append("67%")
    if re.search(r"\bone[\s-]?third\b", t):
        res.append("33%")
    if re.search(r"\bthird\b", t) and "one third" not in t:
        res.append("33%")
    if re.search(r"\bquarter\b", t):
        res.append("25%")
    if re.search(r"\bhalf\b", t):
        res.append("50%")
    return res

def check_mission_consistency(
    data: List[Dict[str, Any]]
) -> List[Dict[str, Any]]:
    """
    检查一致性：
    - ~key(Value) 结构
    - 百分比一致性（忽略 100%；若原文在受检位置包含可替换的分数字词，则允许译文使用对应百分比）
    - 数字一致性（只检查冒号后的行和括号内的数字，忽略键值内的数字，比较为多重集合）
    - 换行符一致性（同时考虑实际换行和转义的 '\n'）
    """
    inconsistencies = []

    # 匹配形如 ~key(Value) 的结构，捕获 key 和 Value，支持空格与半/全角括号
    re_key_value = re.compile(r"~\s*(\w+)\s*[\(（](.*?)[\)）]")
    # 匹配括号内的内容（半角/全角）
    re_parenthesis = re.compile(r"[\(（]([^）\)]*)[）\)]")
    # 匹配冒号后到行尾的内容（: 或 ：）
    re_colon_to_eol = re.compile(r"[:：]\s*([^\n\r]*)")
    # 匹配百分数字符串，如 "15%" "100%"
    re_percentage = re.compile(r"\d+%")
    # 匹配数字（用于提取括号/冒号内容内的纯数字）
    re_digits = re.compile(r"\d+")

    for entry in data:
        original_raw = entry.get('original', '')
        translation_raw = entry.get('translation', '')
        if not translation_raw:
            continue

        # ~key(...) 结构检查
        orig_keys = re_key_value.findall(original_raw)
        trans_keys = re_key_value.findall(translation_raw)
        orig_mis = [m for m in orig_keys if m not in trans_keys]
        trans_mis = [m for m in trans_keys if m not in orig_keys]

        # 先移除 ~key(...) 的内容，避免重复检查键值内的数字/百分比
        original_nokey = re_key_value.sub('', original_raw)
        translation_nokey = re_key_value.sub('', translation_raw)

        # 收集受检区域：冒号后的内容 和 括号内的内容（分别从原文和译文中）
        orig_colon_parts = re_colon_to_eol.findall(original_nokey)
        trans_colon_parts = re_colon_to_eol.findall(translation_nokey)

        orig_paren_parts = re_parenthesis.findall(original_nokey)
        trans_paren_parts = re_parenthesis.findall(translation_nokey)

        # 从这些受检区域中提取显式百分比
        orig_perc_explicit = []
        for s in orig_colon_parts + orig_paren_parts:
            orig_perc_explicit += re_percentage.findall(s)
        trans_perc = []
        for s in trans_colon_parts + trans_paren_parts:
            trans_perc += re_percentage.findall(s)

        # 从受检区域中识别并映射分数字词（仅从受检区域中查找）
        mapped_from_fraction = []
        for s in orig_colon_parts + orig_paren_parts:
            mapped_from_fraction += _map_fraction_words_to_percent(s)

        # 忽略 100%
        orig_perc_filtered = [p for p in orig_perc_explicit if p != "100%"]
        trans_perc_filtered = [p for p in trans_perc if p != "100%"]

        # 原文用于比较的百分比集合：显式百分比 +（若受检区域包含分数字词）映射出的百分比
        orig_perc_for_compare = orig_perc_filtered + mapped_from_fraction

        # 如果原文在受检位置仅有分数字词（没有显式百分比）且译文在受检位置也没有显式百分比，
        # 则认为百分比兼容（不报错）。否则按多重集合比较。
        if mapped_from_fraction and not orig_perc_filtered and not trans_perc_filtered:
            perc_mis = False
        else:
            perc_mis = Counter(orig_perc_for_compare) != Counter(trans_perc_filtered)

        # 数字提取（仅从受检区域：冒号后的内容和括号内的内容），不包含百分号内的数字
        orig_number_parts = orig_colon_parts + orig_paren_parts
        trans_number_parts = trans_colon_parts + trans_paren_parts
        orig_nums = []
        for s in orig_number_parts:
            # 移除百分号数字再提取纯数字
            s_noperc = re_percentage.sub('', s)
            orig_nums += [int(n) for n in re_digits.findall(s_noperc)]
        trans_nums = []
        for s in trans_number_parts:
            s_noperc = re_percentage.sub('', s)
            trans_nums += [int(n) for n in re_digits.findall(s_noperc)]

        num_mis = Counter(orig_nums) != Counter(trans_nums)

        # 换行检查：同时统计真实换行和转义的 '\n'
        orig_nl = original_raw.count('\n') + original_raw.count('\\n')
        trans_nl = translation_raw.count('\n') + translation_raw.count('\\n')
        nl_mis = orig_nl != trans_nl

        if orig_mis or trans_mis or num_mis or perc_mis or nl_mis:
            perc_report = {
                'original_percentages_explicit': orig_perc_explicit,
                'translation_percentages': trans_perc
            }
            if mapped_from_fraction:
                perc_report['original_percentages_mapped_from_fraction_words'] = mapped_from_fraction

            inconsistencies.append({
                'key': entry.get('key'),
                'id': entry.get('id'),
                'original': original_raw,
                'translation': translation_raw,
                'original_mismatches': orig_mis,
                'translation_mismatches': trans_mis,
                'number_mismatches': {
                    'original_numbers': orig_nums,
                    'translation_numbers': trans_nums
                } if num_mis else {},
                'percentage_mismatches': perc_report if perc_mis else {},
                'newline_mismatch': {
                    'original_newlines': orig_nl,
                    'translation_newlines': trans_nl
                } if nl_mis else {}
            })
    return inconsistencies

def check_item_types(data: List[Dict[str, Any]]) -> List[str]:
    """检查物品类型一致性。"""
    item_type_map: Dict[str, Set[str]] = {}
    inconsistencies = []
    for entry in data:
        if not entry.get('translation'):
            continue
        if '物品类型：' in entry['translation'] and 'Item Type: ' in entry['original']:
            cn_type = entry['translation'].split('物品类型：')[1].split('\\n')[0]
            en_type = entry['original'].split('Item Type: ')[1].split('\\n')[0]
            item_type_map.setdefault(en_type, set()).add(cn_type)
    for en_type, cn_types in item_type_map.items():
        if len(cn_types) > 1:
            inconsistencies.append(f"English type '{en_type}' corresponds to multiple Chinese types: {cn_types}")
    missing_keys = [
        entry['key']
        for entry in data
        if 'Item Type: ' in entry['original'] and '物品类型：' not in entry['translation']
    ]
    if missing_keys:
        inconsistencies.append("Keys of original texts with 'Item Type: ' but missing '物品类型：' in translation:")
        inconsistencies.extend(missing_keys)
    return inconsistencies

def batch_update_stage(failed_ids: List[int], stage: int = 2) -> None:
    """批量更新 entries 的 stage 字段。"""
    if not failed_ids:
        return
    payload = {
        "op": "update",
        "id": failed_ids,
        "stage": stage
    }
    resp = requests.put(f"{API_BASE}/strings", headers=HEADERS, json=payload)
    resp.raise_for_status()
    print(f"成功更新 {len(failed_ids)} 个词条的 stage 为 {stage}。")

# ==============================
# 主流程
# ==============================

def main() -> None:
    # 1. 获取并合并数据
    file_data = fetch_translation_files()
    merged_data = merge_json_data(*file_data)
    save_to_json(merged_data, 'merged_data.json')

    # 2. 检查一致性
    inconsistencies = check_mission_consistency(merged_data)
    if inconsistencies:
        # 过滤：包含任意一种不一致（~key 结构、数字不一致、百分比不一致、换行不一致等）
        filtered = [
            inc for inc in inconsistencies
            if inc.get('original_mismatches') or inc.get('translation_mismatches') or inc.get('number_mismatches') or inc.get('percentage_mismatches') or inc.get('newline_mismatch')
        ]
        save_to_json(filtered, 'inconsistencies.json')
        print(f"发现 {len(filtered)} 个格式不一致项，详情见 inconsistencies.json")
        failed_ids = [inc['id'] for inc in filtered]
        batch_update_stage(failed_ids, stage=2)
    else:
        print("所有格式内容一致，无不一致项。")
        # 3. 检查物品类型
        item_type_issues = check_item_types(merged_data)
        if item_type_issues:
            save_to_json(item_type_issues, 'inconsistencies.json')
            print("检测到物品类型不一致，详情见 inconsistencies.json")
        else:
            print("所有物品类型一致，无不一致项。")

if __name__ == "__main__":
    main()
