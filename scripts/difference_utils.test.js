const assert = require('node:assert/strict');
const test = require('node:test');
const {
    assertSourceOnlyItems,
    buildDifferences,
    decodeIniBuffer,
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

test('accepts English source items', () => {
    assert.doesNotThrow(() => assertSourceOnlyItems([
        { key: 'Source_Key', value: 'English source' },
        { key: 'ui_Japanese', value: '日本' }
    ]));
});

test('rejects any Chinese content before comparison', () => {
    assert.throws(
        () => assertSourceOnlyItems([
            { key: 'Good_Key', value: 'English source' },
            { key: 'Wrong_Key', value: '错误上传的汉化文本' }
        ]),
        /包含 1 条中文内容.*原文差异流程已停止/
    );
});

test('builds source differences against ParaTranz originals', () => {
    const globalItems = toGlobalJsonItems([
        { key: 'Same', value: 'Same source' },
        { key: 'Changed', value: 'New source' },
        { key: 'New', value: 'Brand new source' }
    ]);
    const finalItems = [
        { key: 'Same', original: 'Same source', translation: '相同' },
        { key: 'Changed', original: 'Old source', translation: '旧翻译' }
    ];
    const differences = buildDifferences(globalItems, finalItems);

    assert.deepEqual(differences.map(item => item.key), ['Changed', 'New']);
});
