// 直接读取全部状态的词条，包括有译文的草稿；不使用文件导出结果。
async function fetchFileStrings(client, projectUrl, fileId, config = {}) {
    const entries = [];
    for (let page = 1; ; page++) {
        const response = await client.get(`${projectUrl}/strings`, {
            ...config,
            // 不传 stage；mode=4 保留原文件接口包含的隐藏词条。
            params: { file: fileId, page, pageSize: 1000, orderBy: 'id', mode: 4 }
        });
        const { results, pageCount } = response.data;
        if (!Array.isArray(results) || !Number.isInteger(pageCount)) {
            throw new Error(`ParaTranz 词条分页响应格式错误，file=${fileId}`);
        }
        if (results.length === 0 && page < pageCount) {
            throw new Error(`ParaTranz 词条分页提前返回空页，file=${fileId}`);
        }
        entries.push(...results);
        if (page >= pageCount) return entries;
    }
}

module.exports = { fetchFileStrings };
