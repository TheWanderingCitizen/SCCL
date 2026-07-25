const assert = require('node:assert/strict');
const test = require('node:test');
const {
    buildDifferences,
    decodeIniBuffer,
    detectDifferenceMode,
    normalizeForComparison,
    parseIniContent,
    partitionEncodingDamagedItems,
    toGlobalJsonItems
} = require('./difference_utils');

test('decodes UTF-8 BOM and parses values containing equals signs', () => {
    const buffer = Buffer.concat([
        Buffer.from([0xEF, 0xBB, 0xBF]),
        Buffer.from('Key=中文=内容\r\n', 'utf8')
    ]);
    const decoded = decodeIniBuffer(buffer);

    assert.equal(decoded.encoding, 'UTF-8');
    assert.deepEqual(parseIniContent(decoded.text), [{ key: 'Key', value: '中文=内容' }]);
});

test('decodes UTF-16LE BOM', () => {
    const buffer = Buffer.concat([
        Buffer.from([0xFF, 0xFE]),
        Buffer.from('Key=中文\r\n', 'utf16le')
    ]);

    assert.equal(decodeIniBuffer(buffer).text, 'Key=中文\r\n');
});

test('repairs isolated Windows-1252 bytes without corrupting valid UTF-8', () => {
    const buffer = Buffer.concat([
        Buffer.from('Key=中文 Bob', 'utf8'),
        Buffer.from([0x92]),
        Buffer.from('s ship', 'ascii'),
        Buffer.from([0xA0]),
        Buffer.from('name\n', 'ascii')
    ]);
    const decoded = decodeIniBuffer(buffer);

    assert.equal(decoded.text, 'Key=中文 Bob’s ship\u00A0name\n');
    assert.equal(decoded.encoding, 'UTF-8（含 Windows-1252 单字节）');
    assert.deepEqual(decoded.repairedOffsets.length, 2);
});

test('isolates text that was already irreversibly decoded', () => {
    const decoded = decodeIniBuffer(Buffer.from('Good=normal\nBad=damaged � text', 'utf8'));
    const partitioned = partitionEncodingDamagedItems(parseIniContent(decoded.text));

    assert.deepEqual(partitioned.validItems, [{ key: 'Good', value: 'normal' }]);
    assert.deepEqual(partitioned.damagedItems, [{ key: 'Bad', value: 'damaged � text' }]);
});

test('detects source and translation files by value language', () => {
    const sourceItems = Array.from({ length: 30 }, (_, index) => ({
        key: `Source_${index}`,
        value: `English source ${index}`
    }));
    const translationItems = Array.from({ length: 30 }, (_, index) => ({
        key: `Translation_${index}`,
        value: `中文汉化 ${index}`
    }));

    assert.equal(detectDifferenceMode(sourceItems).mode, 'source');
    assert.equal(detectDifferenceMode(translationItems).mode, 'translation');
    assert.equal(detectDifferenceMode(sourceItems, 'translation').mode, 'translation');
});

test('normalizes Unicode composition and whitespace for comparison', () => {
    assert.equal(normalizeForComparison('  Cafe\u0301\r\nname '), normalizeForComparison('Café name'));
});

test('builds source differences against ParaTranz originals', () => {
    const globalItems = toGlobalJsonItems([
        { key: 'Same', value: 'Same source' },
        { key: 'Changed', value: 'New source' },
        { key: 'New', value: 'Brand new source' }
    ], 'source');
    const finalItems = [
        { key: 'Same', original: 'Same source', translation: '相同' },
        { key: 'Changed', original: 'Old source', translation: '旧翻译' }
    ];
    const result = buildDifferences(globalItems, finalItems, 'source');

    assert.deepEqual(result.differences.map(item => item.key), ['Changed', 'New']);
    assert.deepEqual(result.missingKeys, []);
});

test('builds translation differences with the ParaTranz original field', () => {
    const globalItems = toGlobalJsonItems([
        { key: 'Same', value: '相同翻译' },
        { key: 'Changed', value: '新翻译' },
        { key: 'Unknown', value: '未知翻译' }
    ], 'translation');
    const finalItems = [
        { key: 'Same', original: 'Same', translation: '相同翻译' },
        { key: 'Changed', original: 'Changed source', translation: '旧翻译', context: 'ctx' }
    ];
    const result = buildDifferences(globalItems, finalItems, 'translation');

    assert.deepEqual(result.differences, [{
        key: 'Changed',
        original: 'Changed source',
        translation: '新翻译',
        context: 'ctx'
    }]);
    assert.deepEqual(result.missingKeys, ['Unknown']);
});
