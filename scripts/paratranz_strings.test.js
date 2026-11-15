const { test } = require('node:test');
const assert = require('node:assert/strict');
const { fetchFileStrings } = require('./paratranz_strings');

test('keeps draft translations and retrieves every page without a stage filter', async () => {
    const calls = [];
    const draft = { id: 1, key: 'draft', stage: 0, translation: '草稿译文' };
    const checked = { id: 2, key: 'checked', stage: 3, translation: '已检查译文' };
    const client = { get: async (url, config) => {
        calls.push({ url, config });
        return { data: { pageCount: 2, results: config.params.page === 1 ? [draft] : [checked] } };
    } };
    assert.deepEqual(await fetchFileStrings(client, 'https://example.test/projects/8340', 42,
        { headers: { Authorization: 'test-token' } }), [draft, checked]);
    assert.deepEqual(calls.map(call => call.config.params.page), [1, 2]);
    for (const { url, config } of calls) {
        assert.equal(url, 'https://example.test/projects/8340/strings');
        assert.equal(config.params.file, 42);
        assert.equal(config.params.mode, 4);
        assert.equal(config.headers.Authorization, 'test-token');
        assert.equal(Object.hasOwn(config.params, 'stage'), false);
    }
});

test('supports empty files and rejects malformed or prematurely empty pages', async () => {
    const client = data => ({ get: async () => ({ data }) });
    assert.deepEqual(await fetchFileStrings(client({ results: [], pageCount: 0 }), 'url', 1), []);
    await assert.rejects(fetchFileStrings(client({ results: [] }), 'url', 1), /响应格式错误/);
    await assert.rejects(fetchFileStrings(client({ results: [], pageCount: 2 }), 'url', 1), /提前返回空页/);
});
