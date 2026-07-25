const { TextDecoder } = require('util');

const UTF8_BOM = Buffer.from([0xEF, 0xBB, 0xBF]);
const UTF16_LE_BOM = Buffer.from([0xFF, 0xFE]);
const UTF16_BE_BOM = Buffer.from([0xFE, 0xFF]);
const HAN_CHARACTER_PATTERN = /\p{Script=Han}/u;
// 英文原文中用于显示日语语言名称的合法例外。
const SOURCE_HAN_ALLOWLIST = new Set(['ui_Japanese']);

function startsWithBytes(buffer, prefix) {
    return buffer.length >= prefix.length && prefix.every((byte, index) => buffer[index] === byte);
}

function isContinuationByte(byte) {
    return byte >= 0x80 && byte <= 0xBF;
}

function getValidUtf8SequenceLength(buffer, index) {
    const first = buffer[index];

    if (first <= 0x7F) {
        return 1;
    }

    const second = buffer[index + 1];
    const third = buffer[index + 2];
    const fourth = buffer[index + 3];

    if (first >= 0xC2 && first <= 0xDF && isContinuationByte(second)) {
        return 2;
    }

    if (first === 0xE0 && second >= 0xA0 && second <= 0xBF && isContinuationByte(third)) {
        return 3;
    }

    if (
        ((first >= 0xE1 && first <= 0xEC) || (first >= 0xEE && first <= 0xEF))
        && isContinuationByte(second)
        && isContinuationByte(third)
    ) {
        return 3;
    }

    if (first === 0xED && second >= 0x80 && second <= 0x9F && isContinuationByte(third)) {
        return 3;
    }

    if (
        first === 0xF0
        && second >= 0x90
        && second <= 0xBF
        && isContinuationByte(third)
        && isContinuationByte(fourth)
    ) {
        return 4;
    }

    if (
        first >= 0xF1
        && first <= 0xF3
        && isContinuationByte(second)
        && isContinuationByte(third)
        && isContinuationByte(fourth)
    ) {
        return 4;
    }

    if (
        first === 0xF4
        && second >= 0x80
        && second <= 0x8F
        && isContinuationByte(third)
        && isContinuationByte(fourth)
    ) {
        return 4;
    }

    return 0;
}

function decodeMixedUtf8AndWindows1252(buffer) {
    const windows1252Decoder = new TextDecoder('windows-1252', { fatal: true });
    const chunks = [];
    const repairedOffsets = [];
    let validChunkStart = 0;
    let index = 0;

    while (index < buffer.length) {
        const sequenceLength = getValidUtf8SequenceLength(buffer, index);
        if (sequenceLength > 0) {
            index += sequenceLength;
            continue;
        }

        if (validChunkStart < index) {
            chunks.push(buffer.subarray(validChunkStart, index).toString('utf8'));
        }

        chunks.push(windows1252Decoder.decode(buffer.subarray(index, index + 1)));
        repairedOffsets.push(index);
        index++;
        validChunkStart = index;
    }

    if (validChunkStart < buffer.length) {
        chunks.push(buffer.subarray(validChunkStart).toString('utf8'));
    }

    return {
        text: chunks.join(''),
        repairedOffsets
    };
}

function validateDecodedText(text) {
    if (text.includes('\u0000')) {
        throw new Error('global.ini 解码后包含 NUL 字符，文件编码可能识别错误');
    }

    const disallowedControl = text.match(/[\u0080-\u009F]/u);
    if (disallowedControl) {
        const codePoint = disallowedControl[0].codePointAt(0).toString(16).toUpperCase().padStart(4, '0');
        throw new Error(`global.ini 解码后包含不可用控制字符 U+${codePoint}`);
    }
}

function decodeIniBuffer(buffer) {
    let text;
    let encoding;
    let repairedOffsets = [];

    if (startsWithBytes(buffer, UTF16_LE_BOM)) {
        text = new TextDecoder('utf-16le', { fatal: true }).decode(buffer.subarray(UTF16_LE_BOM.length));
        encoding = 'UTF-16LE';
    } else if (startsWithBytes(buffer, UTF16_BE_BOM)) {
        text = new TextDecoder('utf-16be', { fatal: true }).decode(buffer.subarray(UTF16_BE_BOM.length));
        encoding = 'UTF-16BE';
    } else {
        const content = startsWithBytes(buffer, UTF8_BOM)
            ? buffer.subarray(UTF8_BOM.length)
            : buffer;

        try {
            text = new TextDecoder('utf-8', { fatal: true }).decode(content);
            encoding = 'UTF-8';
        } catch {
            const decoded = decodeMixedUtf8AndWindows1252(content);
            text = decoded.text;
            repairedOffsets = decoded.repairedOffsets;
            encoding = 'UTF-8（含 Windows-1252 单字节）';
        }
    }

    validateDecodedText(text);
    return {
        text,
        encoding,
        repairedOffsets
    };
}

function parseIniContent(iniContent) {
    const items = [];

    iniContent.split(/\r?\n/u).forEach((line, lineIndex) => {
        const separatorIndex = line.indexOf('=');
        if (separatorIndex === -1) {
            return;
        }

        const key = line.slice(0, separatorIndex).trim();
        if (!key) {
            throw new Error(`global.ini 第 ${lineIndex + 1} 行的 key 为空`);
        }

        items.push({
            key,
            value: line.slice(separatorIndex + 1).trim()
        });
    });

    return items;
}

function partitionEncodingDamagedItems(items) {
    const validItems = [];
    const damagedItems = [];

    items.forEach(item => {
        if (item.key.includes('\uFFFD') || item.value.includes('\uFFFD')) {
            damagedItems.push(item);
        } else {
            validItems.push(item);
        }
    });

    return {
        validItems,
        damagedItems
    };
}

function findItemsContainingHan(items) {
    return items.filter(item => (
        !SOURCE_HAN_ALLOWLIST.has(item.key)
        && (
            HAN_CHARACTER_PATTERN.test(item.key)
            || HAN_CHARACTER_PATTERN.test(item.value)
        )
    ));
}

function assertSourceOnlyItems(items) {
    const invalidItems = findItemsContainingHan(items);
    if (invalidItems.length === 0) {
        return;
    }

    const exampleKeys = invalidItems.slice(0, 20).map(item => item.key).join(', ');
    throw new Error(
        `global.ini 包含 ${invalidItems.length} 条中文内容，疑似错误上传了汉化文件；`
        + `原文差异流程已停止。示例 key: ${exampleKeys}`
    );
}

function toGlobalJsonItems(items) {
    return items.map(item => ({
        key: item.key,
        original: item.value,
        translation: '',
        context: ''
    }));
}

function normalizeForComparison(value) {
    return String(value ?? '')
        .normalize('NFC')
        .trim()
        .replace(/\s+/gu, ' ');
}

function buildDifferences(globalItems, finalItems) {
    const finalByKey = new Map(finalItems.map(item => [item.key, item]));
    const differences = [];

    globalItems.forEach(globalItem => {
        const finalItem = finalByKey.get(globalItem.key);

        if (!finalItem) {
            differences.push(globalItem);
            return;
        }

        if (
            normalizeForComparison(globalItem.original)
            === normalizeForComparison(finalItem.original)
        ) {
            return;
        }

        differences.push(globalItem);
    });

    return differences;
}

module.exports = {
    assertSourceOnlyItems,
    buildDifferences,
    decodeIniBuffer,
    findItemsContainingHan,
    normalizeForComparison,
    parseIniContent,
    partitionEncodingDamagedItems,
    toGlobalJsonItems
};
