import hashlib
import json
import os
import re
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Set, Tuple

import requests
from requests.adapters import HTTPAdapter, Retry

# ==============================
# 配置
# ==============================
PROJECT_ID = 8340
API_BASE = f"https://paratranz.cn/api/projects/{PROJECT_ID}"
COMMENTS_API_URL = "https://paratranz.cn/api/comments"
AUTHORIZATION = os.getenv("AUTHORIZATION")
REPO_ROOT = Path(__file__).resolve().parent.parent
REPORT_DIR = REPO_ROOT / "reports" / "translation-check"
MERGED_DATA_PATH = REPORT_DIR / "merged_data.json"
REPORT_JSON_PATH = REPORT_DIR / "report.json"
REPORT_MARKDOWN_PATH = REPORT_DIR / "report.md"
AUTO_COMMENT_PREFIX = "<!-- merge_and_check:"
COMMENT_MAX_LENGTH = 500

# 忽略检查：精确 key 与前缀
IGNORE_KEYS = {
    "item_NameFood_bar_snaggle_01_pepper_a"
}
IGNORE_KEY_PREFIXES = [
]

HEADERS = {
    "Authorization": AUTHORIZATION or "",
    "Content-Type": "application/json",
}

# ==============================
# 正则
# ==============================
RE_KEY_VALUE = re.compile(r"~\s*(\w+)\s*[\(（](.*?)[\)）]", re.S)
RE_PAREN = re.compile(r"[\(（]([^）\)]*)[）\)]")
RE_COLON_TO_EOL = re.compile(r"[:：]\s*([^\n\r]*)")
# 百分比：允许可选正负号与空格与小数/全角％，如 "+40 %", "- 5%", "0 %", "145.8％"
RE_PERCENT_SIGNED = re.compile(r"[+-]?\s*\d+(?:\.\d+)?\s*[%％]")
# 去掉标签外壳但保留内部文本：仅删除以字母开头的“类 HTML”标签，保留 <5K> 之类
RE_TAGS_STRIP = re.compile(r"</?[A-Za-z][^>]*>")

# ==============================
# 工具函数
# ==============================
def ensure_auth() -> None:
    if not AUTHORIZATION:
        raise RuntimeError("缺少 AUTHORIZATION 环境变量，请设置后再运行。")

def save_to_json(data: Any, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=4)

def line_count_including_escaped(text: str) -> int:
    # 统一 CRLF -> LF，再统计真实换行与字面量 \n
    physical = text.replace("\r\n", "\n").count("\n")
    escaped = text.count("\\n")
    return physical + escaped

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

def should_ignore_key(key: str) -> bool:
    if key in IGNORE_KEYS:
        return True
    return any(key.startswith(pfx) for pfx in IGNORE_KEY_PREFIXES)

def _session_with_retries() -> requests.Session:
    s = requests.Session()
    retries = Retry(
        total=3,
        backoff_factor=0.5,
        status_forcelist=[429, 500, 502, 503, 504],
        # 评论 POST 不是幂等操作，避免网络重试导致重复评论。
        allowed_methods=["GET", "PUT"],
        respect_retry_after_header=True,
    )
    s.mount("https://", HTTPAdapter(max_retries=retries))
    s.mount("http://", HTTPAdapter(max_retries=retries))
    return s

# ==============================
# HTTP
# ==============================
def fetch_translation_files(
    session: requests.Session,
) -> Tuple[List[List[Dict[str, Any]]], int]:
    url = f"{API_BASE}/files"
    r = session.get(url, headers=HEADERS, timeout=30)
    r.raise_for_status()
    files = r.json() or []
    out: List[List[Dict[str, Any]]] = []
    for item in files:
        fid = item.get("id")
        if fid is None:
            continue
        dl = session.get(f"{API_BASE}/files/{fid}/translation", headers=HEADERS, timeout=60)
        dl.raise_for_status()
        payload = dl.json() or []
        if isinstance(payload, list):
            out.append(payload)
    return out, len(files)

def batch_update_stage(session: requests.Session, ids: List[int], stage: int = 2) -> None:
    if not ids:
        return
    payload = {"op": "update", "id": ids, "stage": stage}
    r = session.put(f"{API_BASE}/strings", headers=HEADERS, json=payload, timeout=60)
    r.raise_for_status()
    print(f"成功更新 {len(ids)} 个词条的 stage 为 {stage}。")


def _comment_signature(issue: Dict[str, Any]) -> str:
    compared = {
        "issue_types": issue.get("issue_types", []),
        "original_mismatches": issue.get("original_mismatches", []),
        "translation_mismatches": issue.get("translation_mismatches", []),
        "number_mismatches": issue.get("number_mismatches", {}),
        "percentage_mismatches": issue.get("percentage_mismatches", {}),
        "newline_mismatch": issue.get("newline_mismatch", {}),
    }
    raw = json.dumps(compared, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()[:16]


def build_issue_comment(issue: Dict[str, Any]) -> str:
    """把检查结果转换为 ParaTranz 词条评论，并附上用于防重复的稳定标记。"""
    marker = f"{AUTO_COMMENT_PREFIX}{_comment_signature(issue)} -->"
    lines = [marker, "**自动格式检查：已标记为“有疑问”**"]

    if "key_placeholder" in issue.get("issue_types", []):
        orig = issue.get("original_mismatches", [])
        trans = issue.get("translation_mismatches", [])
        lines.append(f"- `~key(...)` 占位符不一致：原文缺失于译文 {orig}；译文多出 {trans}")

    if "number" in issue.get("issue_types", []):
        number_mismatches = issue.get("number_mismatches", {})
        for position, values in number_mismatches.items():
            label = "冒号后" if position == "colon" else "括号内"
            lines.append(
                f"- {label}数字不一致：原文 {values.get('original', [])}；"
                f"译文 {values.get('translation', [])}"
            )

    if "percentage" in issue.get("issue_types", []):
        percentages = issue.get("percentage_mismatches", {})
        lines.append(
            "- 百分比不一致：原文 "
            f"{percentages.get('original_percentages_explicit', [])}；"
            f"译文 {percentages.get('translation_percentages', [])}"
        )

    if "newline" in issue.get("issue_types", []):
        newlines = issue.get("newline_mismatch", {})
        lines.append(
            f"- 换行数量不一致：原文 {newlines.get('original_newlines', 0)}；"
            f"译文 {newlines.get('translation_newlines', 0)}"
        )

    comment = "\n".join(lines)
    if len(comment) > COMMENT_MAX_LENGTH:
        comment = comment[:COMMENT_MAX_LENGTH - 1].rstrip() + "…"
    return comment


def fetch_string_comments(session: requests.Session, string_id: int) -> List[Dict[str, Any]]:
    r = session.get(
        COMMENTS_API_URL,
        headers=HEADERS,
        params={"type": "text", "tid": string_id, "pageSize": 100},
        timeout=30,
    )
    r.raise_for_status()
    payload = r.json() or {}
    if isinstance(payload, list):
        return payload
    results = payload.get("results", []) if isinstance(payload, dict) else []
    return results if isinstance(results, list) else []


def add_issue_comments(
    session: requests.Session,
    issues: List[Dict[str, Any]],
) -> Tuple[List[int], List[int], List[Dict[str, Any]]]:
    """发布异常原因；返回新增、已存在和失败的词条 ID。"""
    created_ids: List[int] = []
    existing_ids: List[int] = []
    errors: List[Dict[str, Any]] = []

    for issue in issues:
        string_id = issue.get("id")
        if string_id is None:
            continue
        comment = build_issue_comment(issue)
        marker = comment.split("\n", 1)[0]
        try:
            comments = fetch_string_comments(session, string_id)
            if any(marker in str(item.get("content", "")) for item in comments):
                existing_ids.append(string_id)
                continue

            r = session.post(
                COMMENTS_API_URL,
                headers=HEADERS,
                json={"type": "text", "tid": string_id, "content": comment},
                timeout=30,
            )
            r.raise_for_status()
            created_ids.append(string_id)
        except requests.RequestException as exc:
            errors.append({
                "id": string_id,
                "key": issue.get("key"),
                "error": str(exc),
            })

    if created_ids:
        print(f"成功为 {len(created_ids)} 个有疑问词条添加原因评论。")
    if existing_ids:
        print(f"跳过 {len(existing_ids)} 个已存在相同原因评论的词条。")
    if errors:
        print(f"有 {len(errors)} 个词条的原因评论添加失败，详情见报告。")
    return created_ids, existing_ids, errors

# ==============================
# 文本提取
# ==============================
def remove_compared_key_blocks(text: str, compared_keys: Set[str]) -> str:
    # 避免跨行/嵌套括号误删：仅在“同一行”内删除 ~key(...) 片段
    for k in compared_keys:
        pattern = rf"~\s*{re.escape(k)}\s*[\(（][^\n\r]*?[\)）]"
        text = re.sub(pattern, "", text)
    return text

def extract_key_pairs(text: str) -> List[Tuple[str, str]]:
    return RE_KEY_VALUE.findall(text)

def strip_tags_keep_inner(s: str) -> str:
    return RE_TAGS_STRIP.sub("", s)

def extract_inspected_parts(text: str, compared_keys: Set[str]) -> Tuple[List[str], List[str]]:
    cleaned = remove_compared_key_blocks(text, compared_keys) if compared_keys else text
    colon_parts = RE_COLON_TO_EOL.findall(cleaned)
    # 去掉可能是 URL 的“冒号后”部分（如 http:// 中的第二个冒号）
    colon_parts = [p for p in colon_parts if not re.match(r"\s*//", p)]
    paren_parts = RE_PAREN.findall(cleaned)
    colon_parts = [strip_tags_keep_inner(p) for p in colon_parts]
    paren_parts = [strip_tags_keep_inner(p) for p in paren_parts]
    return colon_parts, paren_parts

# ==============================
# 数值与百分比提取
# ==============================
def extract_percentages_normalized(parts: List[str]) -> List[str]:
    """提取百分比，保留正负号、去内部空格，标准化为 +40% / -5% / 0% 形态（支持全角％与小数）"""
    res: List[str] = []
    for s in parts:
        for m in RE_PERCENT_SIGNED.findall(s):
            # 统一去掉内部空格；全角％也统一为半角% 以比较
            normalized = re.sub(r"\s+", "", m).replace("％", "%")
            res.append(normalized)
    return res

def strip_percentages(text: str) -> str:
    return RE_PERCENT_SIGNED.sub("", text)

def extract_numbers_from_colon(colon_parts: List[str]) -> List[int]:
    """仅取冒号后紧跟的第一个整数（保留可选正负号）"""
    nums: List[int] = []
    for s in colon_parts:
        s_clean = strip_percentages(s)
        m = re.match(r"\s*([+-]?\d+)", s_clean)
        if m:
            nums.append(int(m.group(1)))
    return nums

def extract_numbers_from_paren(paren_parts: List[str]) -> List[int]:
    """括号段：直接识别所有整数（保留可选正负号）"""
    nums: List[int] = []
    for s in paren_parts:
        s_clean = strip_percentages(s)
        nums.extend(int(n) for n in re.findall(r"[+-]?\d+", s_clean))
    return nums

# ==============================
# 主检查逻辑
# ==============================
def check_mission_consistency(data: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    inconsistencies: List[Dict[str, Any]] = []

    for entry in data:
        key = entry.get("key", "") or ""
        if should_ignore_key(key):
            # 完全跳过：不记录为一致/不一致
            continue

        original_raw = entry.get("original", "") or ""
        # —— 这里加 strip：翻译为空或仅空白都跳过检查 ——
        translation_raw = (entry.get("translation") or "").strip()
        if not translation_raw:
            # 翻译内容为空：跳过所有检查
            continue

        # ~key(...) 配对（所有 key 都做）
        orig_pairs = extract_key_pairs(original_raw)
        trans_pairs = extract_key_pairs(translation_raw)
        orig_mis = [m for m in orig_pairs if m not in trans_pairs]
        trans_mis = [m for m in trans_pairs if m not in orig_pairs]
        compared_keys: Set[str] = set([k for k, _ in orig_pairs] + [k for k, _ in trans_pairs])

        orig_colon, orig_paren = extract_inspected_parts(original_raw, compared_keys)
        trans_colon, trans_paren = extract_inspected_parts(translation_raw, compared_keys)

        # 仅 item_* 做“数字 & 百分比”检查
        do_numeric_checks = key.startswith("item_")

        # —— 百分比（完全一致，多重集合相等） ———
        if do_numeric_checks:
            orig_pct = extract_percentages_normalized(orig_colon + orig_paren)

            # 译文先看受检区；若原文有 % 而受检区没抓到，兜底整句（先去标签）
            trans_pct = extract_percentages_normalized(trans_colon + trans_paren)
            if orig_pct and not trans_pct:
                trans_pct = extract_percentages_normalized([strip_tags_keep_inner(translation_raw)])

            perc_mis = Counter(orig_pct) != Counter(trans_pct)
        else:
            perc_mis = False
            orig_pct = []
            trans_pct = []

        # —— 数字（分开比较；括号仅当原文括号里存在数字时才比较） ———
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

        # —— 换行（所有 key 检查） ———
        nl_orig = line_count_including_escaped(original_raw)
        nl_trans = line_count_including_escaped(translation_raw)
        nl_mis = nl_orig != nl_trans

        if orig_mis or trans_mis or num_mis or perc_mis or nl_mis:
            issue_types = []
            if orig_mis or trans_mis:
                issue_types.append("key_placeholder")
            if num_mis:
                issue_types.append("number")
            if perc_mis:
                issue_types.append("percentage")
            if nl_mis:
                issue_types.append("newline")

            perc_report = {}
            if perc_mis:
                perc_report = {
                    "original_percentages_explicit": orig_pct,
                    "translation_percentages": trans_pct,
                }

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
                "issue_types": issue_types,
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

def check_item_types(data: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    item_type_map: Dict[str, Set[str]] = {}
    issues: List[Dict[str, Any]] = []

    # 先统计类型映射，只处理有翻译内容的条目
    for entry in data:
        or_ = entry.get("original") or ""
        tr_raw = entry.get("translation") or ""
        tr = tr_raw.strip()
        if not tr:
            # 翻译为空或仅空白：跳过所有物品类型相关检查
            continue

        if "Item Type: " in or_ and re.search(r"物品类型\s*[:：]", tr):
            cn_type = first_line(re.split(r"物品类型\s*[:：]", tr, 1)[1])
            en_type = first_line(or_.split("Item Type: ", 1)[1])
            item_type_map.setdefault(en_type, set()).add(cn_type)

    # 允许某些 English type 对应多于 1 个中文类型（例如 'Heavy Utility' 允许两种中文对应）
    allowed_multiple: Dict[str, int] = {
        "Heavy Utility": 2,
    }

    for en_type, cn_types in sorted(item_type_map.items()):
        allowed = allowed_multiple.get(en_type, 1)
        if len(cn_types) > allowed:
            issues.append({
                "issue_type": "inconsistent_item_type_mapping",
                "english_type": en_type,
                "chinese_types": sorted(cn_types),
                "allowed_count": allowed,
            })

    # 这里修正：如果翻译为空，则不计入 missing_keys
    missing_keys: List[str] = []
    for entry in data:
        or_ = entry.get("original") or ""
        tr_raw = entry.get("translation") or ""
        tr = tr_raw.strip()

        if "Item Type: " not in or_:
            continue

        # 翻译为空：直接跳过，不算缺失
        if not tr:
            continue

        if not re.search(r"物品类型\s*[:：]", tr):
            missing_keys.append(str(entry.get("key")))

    issues.extend({
        "issue_type": "missing_item_type_label",
        "key": key,
    } for key in sorted(missing_keys))

    return issues


# ==============================
# 报告
# ==============================
ISSUE_TYPE_LABELS = {
    "key_placeholder": "~key 占位符",
    "number": "数字",
    "percentage": "百分比",
    "newline": "换行",
    "inconsistent_item_type_mapping": "物品类型映射不一致",
    "missing_item_type_label": "缺少物品类型标签",
}


def markdown_cell(value: Any, limit: int = 100) -> str:
    text = str(value if value is not None else "")
    text = text.replace("\r", "").replace("\n", "\\n").replace("|", "\\|")
    return f"{text[:limit - 1]}…" if len(text) > limit else text


def build_report(
    file_count: int,
    merged_data: List[Dict[str, Any]],
    format_issues: List[Dict[str, Any]],
    item_type_issues: List[Dict[str, Any]],
    updated_stage_ids: List[int],
    commented_ids: List[int],
    existing_comment_ids: List[int],
    comment_errors: List[Dict[str, Any]],
) -> Dict[str, Any]:
    issue_counts: Counter[str] = Counter()
    for issue in format_issues:
        issue_counts.update(issue["issue_types"])
    issue_counts.update(issue["issue_type"] for issue in item_type_issues)

    total_issues = len(format_issues) + len(item_type_issues)
    return {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "project_id": PROJECT_ID,
        "summary": {
            "status": "failed" if total_issues else "passed",
            "translation_files": file_count,
            "merged_entries": len(merged_data),
            "format_issue_entries": len(format_issues),
            "item_type_issues": len(item_type_issues),
            "total_issues": total_issues,
            "stage_updated_entries": len(updated_stage_ids),
            "comments_added": len(commented_ids),
            "comments_already_present": len(existing_comment_ids),
            "comment_errors": len(comment_errors),
        },
        "issue_counts": dict(sorted(issue_counts.items())),
        "format_issues": format_issues,
        "item_type_issues": item_type_issues,
        "comment_errors": comment_errors,
    }


def render_markdown_report(report: Dict[str, Any], max_rows: int = 100) -> str:
    summary = report["summary"]
    passed = summary["status"] == "passed"
    lines = [
        "# 翻译键值检查报告",
        "",
        f"> {'✅ 检查通过' if passed else '❌ 发现不一致项'}",
        "",
        "## 概览",
        "",
        "| 指标 | 数量 |",
        "| --- | ---: |",
        f"| ParaTranz 文件 | {summary['translation_files']} |",
        f"| 合并后词条 | {summary['merged_entries']} |",
        f"| 格式异常词条 | {summary['format_issue_entries']} |",
        f"| 物品类型问题 | {summary['item_type_issues']} |",
        f"| 问题总数 | **{summary['total_issues']}** |",
        f"| 已更新至 stage 2 | {summary['stage_updated_entries']} |",
        f"| 已添加原因评论 | {summary['comments_added']} |",
        f"| 已存在相同评论 | {summary['comments_already_present']} |",
        f"| 评论添加失败 | {summary['comment_errors']} |",
    ]

    if report["issue_counts"]:
        lines.extend([
            "",
            "## 问题分类",
            "",
            "| 类型 | 数量 |",
            "| --- | ---: |",
        ])
        for issue_type, count in report["issue_counts"].items():
            lines.append(f"| {ISSUE_TYPE_LABELS.get(issue_type, issue_type)} | {count} |")

    format_issues = report["format_issues"]
    if format_issues:
        lines.extend([
            "",
            "## 格式异常词条",
            "",
            "| # | Key | ID | 类型 | 原文 | 译文 |",
            "| ---: | --- | ---: | --- | --- | --- |",
        ])
        for index, issue in enumerate(format_issues[:max_rows], start=1):
            labels = ", ".join(ISSUE_TYPE_LABELS.get(t, t) for t in issue["issue_types"])
            lines.append(
                f"| {index} | {markdown_cell(issue['key'], 60)} | {issue.get('id', '')} "
                f"| {labels} | {markdown_cell(issue['original'])} "
                f"| {markdown_cell(issue['translation'])} |"
            )
        if len(format_issues) > max_rows:
            lines.extend(["", f"> 仅展示前 {max_rows} 项；完整详情见 `report.json`。"])

    item_type_issues = report["item_type_issues"]
    if item_type_issues:
        lines.extend(["", "## 物品类型问题", ""])
        for issue in item_type_issues[:max_rows]:
            if issue["issue_type"] == "inconsistent_item_type_mapping":
                chinese_types = "、".join(issue["chinese_types"])
                lines.append(
                    f"- `{markdown_cell(issue['english_type'])}` 对应了 "
                    f"{len(issue['chinese_types'])} 个中文类型：{markdown_cell(chinese_types)}"
                )
            else:
                lines.append(f"- `{markdown_cell(issue['key'])}` 的译文缺少“物品类型：”。")
        if len(item_type_issues) > max_rows:
            lines.extend(["", f"> 仅展示前 {max_rows} 项；完整详情见 `report.json`。"])

    comment_errors = report["comment_errors"]
    if comment_errors:
        lines.extend(["", "## 原因评论添加失败", ""])
        for error in comment_errors[:max_rows]:
            lines.append(
                f"- `{markdown_cell(error.get('key'), 60)}`（ID {error.get('id', '')}）："
                f"{markdown_cell(error.get('error'), 160)}"
            )
        if len(comment_errors) > max_rows:
            lines.extend(["", f"> 仅展示前 {max_rows} 项；完整详情见 `report.json`。"])

    lines.extend(["", f"_报告生成时间（UTC）：{report['generated_at']}_", ""])
    return "\n".join(lines)


def save_report(report: Dict[str, Any]) -> None:
    save_to_json(report, REPORT_JSON_PATH)
    REPORT_MARKDOWN_PATH.write_text(render_markdown_report(report), encoding="utf-8")


# ==============================
# 主流程
# ==============================
def main() -> None:
    ensure_auth()
    with _session_with_retries() as s:
        file_data, file_count = fetch_translation_files(s)
        merged_data = merge_json_data(*file_data)
        save_to_json(merged_data, MERGED_DATA_PATH)

        format_issues = check_mission_consistency(merged_data)
        item_type_issues = check_item_types(merged_data)
        failed_ids = sorted({
            issue["id"] for issue in format_issues if issue.get("id") is not None
        })
        if failed_ids:
            batch_update_stage(s, failed_ids, stage=2)
        commented_ids, existing_comment_ids, comment_errors = add_issue_comments(
            s,
            format_issues,
        )

        report = build_report(
            file_count,
            merged_data,
            format_issues,
            item_type_issues,
            failed_ids,
            commented_ids,
            existing_comment_ids,
            comment_errors,
        )
        save_report(report)

        summary = report["summary"]
        print("\n=== 翻译键值检查结果 ===")
        print(f"状态: {'通过' if summary['status'] == 'passed' else '失败'}")
        print(f"合并词条: {summary['merged_entries']}")
        print(f"格式异常: {summary['format_issue_entries']}")
        print(f"物品类型问题: {summary['item_type_issues']}")
        print(f"新增原因评论: {summary['comments_added']}")
        print(f"评论添加失败: {summary['comment_errors']}")
        print(f"报告: {REPORT_MARKDOWN_PATH}")

if __name__ == "__main__":
    main()
