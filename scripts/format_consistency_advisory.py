"""非阻断的跨词条格式一致性附加检查。

本模块只返回报告数据，不执行网络请求、不修改 ParaTranz 状态，也不决定
正式检查是否通过。调用方必须将 findings 视为 advisory。
"""

from __future__ import annotations

import re
import unicodedata
from collections import Counter, defaultdict
from decimal import Decimal, InvalidOperation
from typing import Any, Dict, Iterable, List, Mapping, Sequence, Set, Tuple


MIN_TEMPLATE_GROUP_SIZE = 2
MIN_FIXED_CHARACTERS = 4
MIN_TEMPLATE_TEXT_LENGTH = 80
IGNORED_KEY_SUFFIXES = (",P",)

RE_TOKEN = re.compile(
    r"(?P<placeholder>~\s*[A-Za-z_]\w*\s*[\(（][^\n\r]*?[\)）])"
    r"|(?P<tag></?[A-Za-z][^>\n\r]*>)"
    r"|(?P<url>https?://[^\s<>]+)"
    r"|(?P<percentage>[+-]?\s*\d+(?:\.\d+)?\s*[%％])"
    r"|(?P<number>[+-]?\s*\d+(?:\.\d+)?)",
    re.IGNORECASE,
)
RE_PLACEHOLDER_PARTS = re.compile(
    r"~\s*(?P<scope>[A-Za-z_]\w*)\s*[\(（](?P<argument>.*)[\)）]",
    re.DOTALL,
)
RE_TAG_PARTS = re.compile(r"<\s*(?P<closing>/)?\s*(?P<name>[A-Za-z][\w:-]*)")
RE_TEMPLATE_TOKEN = re.compile(r"<(?:PH:[^>]+|TAG:[^>]+|URL|PERCENT|NUMBER)>")
RE_KEY_VALUE = re.compile(r"~\s*(\w+)\s*[\(（](.*?)[\)）]", re.DOTALL)
RE_PAREN = re.compile(r"[\(（]([^）\)]*)[）\)]")
RE_COLON_TO_EOL = re.compile(r"[:：]\s*([^\n\r]*)")
RE_PERCENT_SIGNED = re.compile(r"[+-]?\s*\d+(?:\.\d+)?\s*[%％]")
RE_NUMBER_SIGNED = re.compile(r"[+-]?\s*\d+(?:\.\d+)?")
RE_TAGS_STRIP = re.compile(r"</?[A-Za-z][^>]*>")

REASON_LABELS = {
    "exact_translation_conflict": "相同原文出现不同译文",
    "template_translation_conflict": "参数模板出现不同固定文本",
    "missing_translation_in_template": "模板组内存在空译文",
    "placeholder_mismatch": "占位符数量或名称不一致",
    "tag_structure_mismatch": "成对标签结构不一致",
    "number_mismatch": "item_Desc 数字不一致",
    "percentage_mismatch": "item_Desc 百分比不一致",
    "newline_mismatch": "换行数量不一致",
    "url_mismatch": "URL 不一致",
}

Entry = Dict[str, Any]
Finding = Dict[str, Any]


def normalize_text(value: Any) -> str:
    text = unicodedata.normalize("NFKC", str(value or ""))
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    lines = [re.sub(r"[\t \f\v]+", " ", line).rstrip() for line in text.split("\n")]
    return "\n".join(lines).strip()


def line_break_count(value: Any) -> int:
    text = str(value or "").replace("\r\n", "\n").replace("\r", "\n")
    return text.count("\n") + text.count("\\n")


def placeholder_signature(raw: str) -> str:
    normalized = normalize_text(raw)
    match = RE_PLACEHOLDER_PARTS.fullmatch(normalized)
    if not match:
        return normalized
    argument = re.sub(r"\s+", "", match.group("argument"))
    return f"~{match.group('scope')}({argument})"


def tag_signature(raw: str) -> str:
    normalized = normalize_text(raw)
    match = RE_TAG_PARTS.match(normalized)
    if not match:
        return normalized
    closing = "/" if match.group("closing") else ""
    return f"<{closing}{match.group('name').lower()}>"


def tag_name(signature: str) -> str:
    return signature.strip("<>").lstrip("/").lower()


def extract_tag_signatures(value: Any) -> List[str]:
    text = normalize_text(value)
    return [
        tag_signature(match.group(0))
        for match in RE_TOKEN.finditer(text)
        if match.lastgroup == "tag"
    ]


def balanced_tag_names(value: Any) -> Set[str]:
    openings: Counter[str] = Counter()
    closings: Counter[str] = Counter()
    for signature in extract_tag_signatures(value):
        name = tag_name(signature)
        if signature.startswith("</"):
            closings[name] += 1
        else:
            openings[name] += 1
    return {
        name
        for name, count in openings.items()
        if count > 0 and closings.get(name) == count
    }


def tokenized_skeleton(value: Any) -> Tuple[str, List[Tuple[str, str]]]:
    text = normalize_text(value)
    output: List[str] = []
    slots: List[Tuple[str, str]] = []
    cursor = 0
    paired_tags = balanced_tag_names(text)

    for match in RE_TOKEN.finditer(text):
        output.append(text[cursor:match.start()])
        kind = match.lastgroup or "unknown"
        raw = match.group(0)
        if kind == "placeholder":
            parts = RE_PLACEHOLDER_PARTS.fullmatch(raw)
            scope = parts.group("scope").lower() if parts else "unknown"
            output.append(f"<PH:{scope}>")
            slots.append(("placeholder", placeholder_signature(raw)))
        elif kind == "tag":
            signature = tag_signature(raw)
            if tag_name(signature) in paired_tags:
                output.append(f"<TAG:{signature[1:-1]}>")
                slots.append(("tag", signature))
            else:
                output.append(raw)
        elif kind == "url":
            output.append("<URL>")
            slots.append(("url", normalize_text(raw)))
        elif kind == "percentage":
            output.append("<PERCENT>")
            slots.append(("percentage", canonical_number(raw)))
        elif kind == "number":
            output.append("<NUMBER>")
            slots.append(("number", canonical_number(raw)))
        cursor = match.end()

    output.append(text[cursor:])
    return normalize_text("".join(output)), slots


def fixed_character_count(skeleton: str) -> int:
    fixed = RE_TEMPLATE_TOKEN.sub("", skeleton)
    return len(re.findall(r"[A-Za-z0-9\u3400-\u9fff]", fixed))


def canonical_number(raw: str) -> str:
    normalized = unicodedata.normalize("NFKC", raw)
    normalized = re.sub(r"\s+", "", normalized).rstrip("%")
    normalized = normalized.lstrip("+-") or "0"
    try:
        number = abs(Decimal(normalized))
    except InvalidOperation:
        return normalized
    if number == number.to_integral_value():
        return str(number.quantize(Decimal("1")))
    return format(number.normalize(), "f")


def remove_compared_key_blocks(text: str, compared_keys: Iterable[str]) -> str:
    for key in compared_keys:
        pattern = rf"~\s*{re.escape(key)}\s*[\(（][^\n\r]*?[\)）]"
        text = re.sub(pattern, "", text)
    return text


def extract_inspected_parts(text: Any) -> Tuple[List[str], List[str]]:
    normalized = normalize_text(text)
    compared_keys = {key for key, _ in RE_KEY_VALUE.findall(normalized)}
    cleaned = remove_compared_key_blocks(normalized, compared_keys)
    colon_parts = [
        RE_TAGS_STRIP.sub("", part)
        for part in RE_COLON_TO_EOL.findall(cleaned)
        if not re.match(r"\s*//", part)
    ]
    paren_parts = [RE_TAGS_STRIP.sub("", part) for part in RE_PAREN.findall(cleaned)]
    return colon_parts, paren_parts


def extract_percentages(parts: Iterable[str]) -> List[str]:
    return [
        canonical_number(match)
        for part in parts
        for match in RE_PERCENT_SIGNED.findall(part)
    ]


def strip_percentages(text: str) -> str:
    return RE_PERCENT_SIGNED.sub("", text)


def extract_colon_numbers(parts: Iterable[str]) -> List[str]:
    numbers: List[str] = []
    for part in parts:
        match = re.match(r"\s*([+-]?\s*\d+(?:\.\d+)?)", strip_percentages(part))
        if match:
            numbers.append(canonical_number(match.group(1)))
    return numbers


def extract_paren_numbers(parts: Iterable[str]) -> List[str]:
    return [
        canonical_number(match)
        for part in parts
        for match in RE_NUMBER_SIGNED.findall(strip_percentages(part))
    ]


def source_file_id(entry: Mapping[str, Any]) -> int:
    source = entry.get("_source_file") or {}
    value = source.get("id") if isinstance(source, Mapping) else None
    if value is None:
        value = entry.get("file")
    return int(value or -1)


def effective_entries(corpus: Sequence[Entry]) -> List[Entry]:
    by_key: Dict[str, List[Entry]] = defaultdict(list)
    without_key: List[Entry] = []
    for entry in corpus:
        key = entry.get("key")
        if key is None:
            without_key.append(entry)
        else:
            by_key[str(key)].append(entry)

    selected = [
        max(
            members,
            key=lambda item: (source_file_id(item), int(item.get("id") or -1)),
        )
        for members in by_key.values()
    ]
    selected.extend(without_key)
    return selected


def should_ignore(entry: Mapping[str, Any]) -> bool:
    key = str(entry.get("key") or "")
    return any(key.endswith(suffix) for suffix in IGNORED_KEY_SUFFIXES)


def add_finding(findings: List[Finding], issue_type: str, keys: Iterable[str]) -> None:
    normalized_keys = sorted({str(key) for key in keys if key is not None})
    if not normalized_keys:
        return
    findings.append({
        "issue_type": issue_type,
        "reason": REASON_LABELS.get(issue_type, issue_type),
        "keys": normalized_keys,
        "blocking": False,
    })


def structural_findings(entries: Sequence[Entry]) -> List[Finding]:
    findings: List[Finding] = []
    for entry in entries:
        key = str(entry.get("key") or "")
        original = entry.get("original") or ""
        translation = entry.get("translation") or ""
        if not normalize_text(original) or not normalize_text(translation):
            continue

        _, source_slots = tokenized_skeleton(original)
        _, target_slots = tokenized_skeleton(translation)
        source_by_type: Dict[str, List[str]] = defaultdict(list)
        target_by_type: Dict[str, List[str]] = defaultdict(list)
        for kind, value in source_slots:
            source_by_type[kind].append(value)
        for kind, value in target_slots:
            target_by_type[kind].append(value)

        if Counter(source_by_type["placeholder"]) != Counter(target_by_type["placeholder"]):
            add_finding(findings, "placeholder_mismatch", [key])
        if source_by_type["url"] and source_by_type["url"] != target_by_type["url"]:
            add_finding(findings, "url_mismatch", [key])

        expected_tag_names = balanced_tag_names(original)
        if expected_tag_names:
            source_tags = [
                signature
                for signature in extract_tag_signatures(original)
                if tag_name(signature) in expected_tag_names
            ]
            target_tags = [
                signature
                for signature in extract_tag_signatures(translation)
                if tag_name(signature) in expected_tag_names
            ]
            if source_tags != target_tags:
                add_finding(findings, "tag_structure_mismatch", [key])

        if key.startswith("item_Desc"):
            source_colon, source_paren = extract_inspected_parts(original)
            target_colon, target_paren = extract_inspected_parts(translation)
            source_percentages = extract_percentages(source_colon + source_paren)
            target_percentages = extract_percentages(target_colon + target_paren)
            if source_percentages and not target_percentages:
                target_percentages = extract_percentages([translation])
            if Counter(source_percentages) != Counter(target_percentages):
                add_finding(findings, "percentage_mismatch", [key])

            source_colon_numbers = extract_colon_numbers(source_colon)
            target_colon_numbers = extract_colon_numbers(target_colon)
            source_paren_numbers = extract_paren_numbers(source_paren)
            target_paren_numbers = extract_paren_numbers(target_paren)
            colon_mismatch = Counter(source_colon_numbers) != Counter(target_colon_numbers)
            paren_mismatch = (
                Counter(source_paren_numbers) != Counter(target_paren_numbers)
                if source_paren_numbers
                else False
            )
            if colon_mismatch or paren_mismatch:
                add_finding(findings, "number_mismatch", [key])

        if line_break_count(original) != line_break_count(translation):
            add_finding(findings, "newline_mismatch", [key])
    return findings


def build_format_consistency_advisory(corpus: Sequence[Entry]) -> Dict[str, Any]:
    effective = effective_entries(corpus)
    analysis_entries = [entry for entry in effective if not should_ignore(entry)]
    usable = [entry for entry in analysis_entries if normalize_text(entry.get("original"))]
    template_entries = [
        entry
        for entry in usable
        if len(normalize_text(entry.get("original"))) >= MIN_TEMPLATE_TEXT_LENGTH
    ]

    exact_buckets: Dict[str, List[Entry]] = defaultdict(list)
    explicit_buckets: Dict[str, List[Entry]] = defaultdict(list)
    for entry in template_entries:
        original = normalize_text(entry.get("original"))
        exact_buckets[original].append(entry)
        skeleton, slots = tokenized_skeleton(original)
        variable_types = [kind for kind, _ in slots if kind != "tag"]
        if variable_types and fixed_character_count(skeleton) >= MIN_FIXED_CHARACTERS:
            explicit_buckets[skeleton].append(entry)

    findings = structural_findings(analysis_entries)
    for members in exact_buckets.values():
        if len(members) < MIN_TEMPLATE_GROUP_SIZE:
            continue
        translations = {
            normalize_text(entry.get("translation"))
            for entry in members
            if normalize_text(entry.get("translation"))
        }
        keys = [str(entry.get("key")) for entry in members]
        if any(not normalize_text(entry.get("translation")) for entry in members):
            add_finding(findings, "missing_translation_in_template", keys)
        if len(translations) > 1:
            add_finding(findings, "exact_translation_conflict", keys)

    for skeleton, members in explicit_buckets.items():
        distinct_originals = {normalize_text(entry.get("original")) for entry in members}
        if len(members) < MIN_TEMPLATE_GROUP_SIZE or len(distinct_originals) < 2:
            continue
        translation_skeletons = {
            tokenized_skeleton(entry.get("translation"))[0]
            for entry in members
            if normalize_text(entry.get("translation"))
        }
        keys = [str(entry.get("key")) for entry in members]
        if any(not normalize_text(entry.get("translation")) for entry in members):
            add_finding(findings, "missing_translation_in_template", keys)
        if len(translation_skeletons) > 1:
            add_finding(findings, "template_translation_conflict", keys)

    unique: Dict[Tuple[str, Tuple[str, ...]], Finding] = {}
    for finding in findings:
        identity = finding["issue_type"], tuple(finding["keys"])
        unique[identity] = finding
    findings = sorted(
        unique.values(),
        key=lambda item: (item["issue_type"], item["keys"]),
    )
    counts = Counter(finding["issue_type"] for finding in findings)
    return {
        "blocking": False,
        "effective_entries": len(effective),
        "analyzed_entries": len(analysis_entries),
        "ignored_key_suffixes": list(IGNORED_KEY_SUFFIXES),
        "ignored_key_suffix_entries": len(effective) - len(analysis_entries),
        "minimum_template_text_length": MIN_TEMPLATE_TEXT_LENGTH,
        "long_text_entries": len(template_entries),
        "finding_count": len(findings),
        "finding_counts": dict(sorted(counts.items())),
        "findings": findings,
    }
