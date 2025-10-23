import requests
import json
import re
import os
from typing import List, Dict, Any, Set

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

def check_mission_consistency(
    data: List[Dict[str, Any]]
) -> List[Dict[str, Any]]:
    """
    检查以下一致性：
    1. ~key(Value) 结构
    2. 百分比一致性
    3. 特征数字：冒号数字、括号数字（同时支持 ASCII 和全角括号）
    4. 换行符一致性（同时考虑实际换行和转义的 '\n'）
    """
    inconsistencies = []

    # 匹配形如 ~key(Value) 的结构，捕获 key 和 Value
    re_key_value = re.compile(r"~(\w+)\((.*?)\)")
    # 匹配以冒号(:或：)开头，后面可能有空格，紧跟一个整数（可为负），后接换行或字符串结尾。用于捕获如 ": 8", "：-5"
    re_num_colon = re.compile(r"[:：]\s*(-?\d+)\s*(?:\n|$)")
    # 匹配括号内的所有内容，支持 ASCII 括号 () 和中文全角括号 （）
    re_num_parenthesis = re.compile(r"[\(（]([^）\)]*)[）\)]")
    # 匹配百分数字符串，如 "15%" "100%"
    re_percentage = re.compile(r"\d+%")
    # 匹配数字，辅助于括号内容查找所有数字
    re_digits = re.compile(r"\d+")

    for entry in data:
        original_raw = entry.get('original', '')
        translation_raw = entry.get('translation', '')
        # 为了不破坏中文全角符号，仍保留原文本，后续需要同时处理
        original = original_raw.replace(' ', '')
        translation = translation_raw.replace(' ', '')
        if not translation:
            continue

        # ~key(Value) 检查
        orig_keys = re_key_value.findall(original)
        trans_keys = re_key_value.findall(translation)
        orig_mis = [m for m in orig_keys if m not in trans_keys]
        trans_mis = [m for m in trans_keys if m not in orig_keys]

        # 数字检查：冒号数字
        orig_nums = [int(x) for x in re_num_colon.findall(original)]
        trans_nums = [int(x) for x in re_num_colon.findall(translation)]

        # 数字检查：括号内所有数字（支持全角和半角括号）
        orig_paren_contents = re_num_parenthesis.findall(original)
        trans_paren_contents = re_num_parenthesis.findall(translation)
        orig_nums += [int(num) for content in orig_paren_contents for num in re_digits.findall(content)]
        trans_nums += [int(num) for content in trans_paren_contents for num in re_digits.findall(content)]

        num_mis = (orig_nums != trans_nums)

        # 百分比检查
        orig_perc = re_percentage.findall(original)
        trans_perc = re_percentage.findall(translation)
        # 处理中文中可能使用 "百分百" 或 "完全" 表示 100%
        if any(k in translation_raw for k in ["百分百", "完全"]):
            if "100%" not in trans_perc:
                trans_perc.append("100%")
        perc_mis = sorted(orig_perc) != sorted(trans_perc)

        # 换行检查：同时统计真实换行和转义的 '\n'
        orig_nl = original_raw.count('\n') + original_raw.count('\\n')
        trans_nl = translation_raw.count('\n') + translation_raw.count('\\n')
        nl_mis = orig_nl != trans_nl

        if orig_mis or trans_mis or num_mis or perc_mis or nl_mis:
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
                'percentage_mismatches': {
                    'original_percentages': orig_perc,
                    'translation_percentages': trans_perc
                } if perc_mis else {},
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
