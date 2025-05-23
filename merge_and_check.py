import requests
import json
import re
import os

def merge_json_data(*lists):
    """Merge multiple JSON lists, keeping data with the highest ID."""
    merged_dict = {}
    for json_list in lists:
        for item in json_list:
            current_item = merged_dict.get(item['key'])
            if current_item:
                if item['id'] > current_item['id']:
                    merged_dict[item['key']] = item
            else:
                merged_dict[item['key']] = item
    return list(merged_dict.values())

def save_to_json(data, output_file_path):
    """Save merged JSON data to a file."""
    with open(output_file_path, 'w', encoding='utf-8') as json_file:
        json.dump(data, json_file, ensure_ascii=False, indent=4)

def check_mission_consistency(data):
    """Check for consistency in ~key(Value) format, percentages, sequential numbers, and newline counts."""
    inconsistencies = []
    pattern_key_value = re.compile(r"~(\w+)\((.*?)\)")
    pattern_number_with_colon_newline = re.compile(r"[:：]\s*(-?\d+)\s*(?:\n|$)")
    pattern_percentage = re.compile(r"\d+%")

    for entry in data:
        original = entry.get('original', '').replace(' ', '')
        translation = entry.get('translation', '').replace(' ', '')
        if translation == '':
            continue

        # Check ~key(Value) consistency
        original_matches = pattern_key_value.findall(original)
        translation_matches = pattern_key_value.findall(translation)

        if sorted(original_matches) != sorted(translation_matches):
            original_mismatches = [match for match in original_matches if match not in translation_matches]
            translation_mismatches = [match for match in translation_matches if match not in original_matches]
        else:
            original_mismatches = []
            translation_mismatches = []

        # Check numeric consistency
        original_numbers = [match for match in pattern_number_with_colon_newline.findall(original)]
        if original_numbers:
            translation_numbers = [match for match in pattern_number_with_colon_newline.findall(translation)]
            original_values = [int(match) for match in original_numbers]
            translation_values = [int(match) for match in translation_numbers]
            if original_values != translation_values:
                number_mismatches = {
                    'original_numbers': original_numbers,
                    'translation_numbers': translation_numbers
                }
            else:
                number_mismatches = {}
        else:
            number_mismatches = {}

        # Check percentage consistency
        original_percentages = pattern_percentage.findall(original)
        translation_percentages = pattern_percentage.findall(translation)

        if any(keyword in entry.get('translation', '') for keyword in ["百分百", "完全"]):
            translation_percentages = [p if p != "100%" else "100%" for p in translation_percentages]
            if "100%" not in translation_percentages:
                translation_percentages.append("100%")

        filtered_translation_percentages = [p for p in original_percentages if p in translation_percentages]
        if sorted(original_percentages) != sorted(filtered_translation_percentages):
            percentage_mismatches = {
                'original_percentages': original_percentages,
                'translation_percentages': translation_percentages
            }
        else:
            percentage_mismatches = {}

        # Check newline count consistency
        original_newline_count = original.count('\\n')
        translation_newline_count = translation.count('\\n')
        if original_newline_count != translation_newline_count:
            newline_mismatch = {
                'original_newlines': original_newline_count,
                'translation_newlines': translation_newline_count
            }
        else:
            newline_mismatch = {}

        # Collect inconsistencies
        if (original_mismatches or translation_mismatches or 
            number_mismatches or percentage_mismatches or newline_mismatch):
            inconsistencies.append({
                'key': entry.get('key'),
                'id': entry.get('id'),
                'original': entry.get('original', ''),
                'translation': entry.get('translation', ''),
                'original_mismatches': original_mismatches,
                'translation_mismatches': translation_mismatches,
                'number_mismatches': number_mismatches,
                'percentage_mismatches': percentage_mismatches,
                'newline_mismatch': newline_mismatch
            })

    return inconsistencies

def check_item_types(data):
    unique_item_types = {item['translation'].split('物品类型：')[1].split('\\n')[0] for item in data if '物品类型：' in item['translation']}
    unique_item_types_o = {item['original'].split('Item Type: ')[1].split('\\n')[0] for item in data if 'Item Type: ' in item['original']}

    item_type_mapping = {}
    inconsistencies = []

    for item in data:
        if item['translation'] == '':
            continue
        if '物品类型：' in item['translation'] and 'Item Type: ' in item['original']:
            cn_type = item['translation'].split('物品类型：')[1].split('\\n')[0]
            en_type = item['original'].split('Item Type: ')[1].split('\\n')[0]
            if en_type not in item_type_mapping:
                item_type_mapping[en_type] = set()
            item_type_mapping[en_type].add(cn_type)

    for en_type, cn_types in item_type_mapping.items():
        if len(cn_types) > 1:
            print(f"English type '{en_type}' : {cn_types}")
            inconsistencies.append(f"English type '{en_type}' corresponds to multiple Chinese types: {cn_types}")

    missing_translations_keys = [item['key'] for item in data if 'Item Type: ' in item['original'] and '物品类型：' not in item['translation']]
    if missing_translations_keys:
        inconsistencies.append("Keys of original texts with 'Item Type: ' but missing '物品类型：' in translation:")
        inconsistencies.extend(missing_translations_keys)

    return inconsistencies

def batch_update_stage(failed_ids, stage=2):
    project_id = 8340
    headers = {
        'Authorization': f"{os.getenv('AUTHORIZATION')}",
        'Content-Type': 'application/json'
    }
    update_url = f"https://paratranz.cn/api/projects/{project_id}/strings"
    update_payload = {
        "op": "update",
        "id": failed_ids,
        "stage": stage
    }
    response = requests.put(update_url, headers=headers, json=update_payload)
    response.raise_for_status()
    print(f"成功更新 {len(failed_ids)} 个词条的 stage 为 {stage}。")

def main():
    is_error = False
    url_checkfiles = "https://paratranz.cn/api/projects/8340/files"
    headers = {'Authorization': f"{os.getenv('AUTHORIZATION')}",}

    json_data_lists = []
    response = requests.get(url_checkfiles, headers=headers)
    response.raise_for_status()

    for item in response.json():
        url_download = f"https://paratranz.cn/api/projects/8340/files/{item['id']}/translation"
        file_response = requests.get(url_download, headers=headers)
        file_response.raise_for_status()
        json_data_lists.append(file_response.json())

    merged_data = merge_json_data(*json_data_lists)
    save_to_json(merged_data, 'merged_data.json')

    inconsistencies = check_mission_consistency(merged_data)
    if inconsistencies:
        filtered_inconsistencies = []
        failed_entries = []
        for inconsistency in inconsistencies:
            if (inconsistency['original_mismatches'] or inconsistency['translation_mismatches'] or
                inconsistency['newline_mismatch']):
                filtered_inconsistencies.append(inconsistency)
                failed_entries.append({'key': inconsistency['key'], 'id': inconsistency['id']})

        print(f"发现 {len(filtered_inconsistencies)} 个格式不一致项：")
        for inconsistency in filtered_inconsistencies:
            print(f"ID: {inconsistency['id']}")
            print(f"Key: {inconsistency['key']}")
            print(f"Original: {inconsistency['original']}")
            print(f"Translation: {inconsistency['translation']}")
            print(f"Original Mismatches: {inconsistency['original_mismatches']}")
            print(f"Translation Mismatches: {inconsistency['translation_mismatches']}")
            if inconsistency.get('newline_mismatch'):
                print(f"Newline Mismatch: {inconsistency['newline_mismatch']}")
            print("-" * 40)

        save_to_json(filtered_inconsistencies, 'inconsistencies.json')
        is_error = True
        print("不一致的结果已保存到 inconsistencies.json 文件。")

        if failed_entries:
            failed_ids = [entry['id'] for entry in failed_entries]
            batch_update_stage(failed_ids, stage=2)
    else:
        print("所有格式内容一致，无不一致项。")
        item_type_inconsistencies = check_item_types(merged_data)
        if item_type_inconsistencies:
            print("检测到物品类型不一致，以下是问题列表")
            print(item_type_inconsistencies)
            if not is_error:
                save_to_json(item_type_inconsistencies, 'inconsistencies.json')
        else:
            print("所有物品类型一致，无不一致项")

if __name__ == "__main__":
    main()
