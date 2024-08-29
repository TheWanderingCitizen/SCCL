const axios = require('axios');
const fs = require('fs');

// API 配置
const authHeader = {
    headers: {
        'Authorization': process.env.AUTHORIZATION
    }
};

// 读取并转换 INI 文件为 JSON
function convertIniToJson() {
    // 读取 INI 文件的二进制数据
    const iniContentBuffer = fs.readFileSync('global.ini');

    // 计算替换后的缓冲区大小
    let estimatedSize = iniContentBuffer.length;
    for (let i = 0; i < iniContentBuffer.length; i++) {
        if (iniContentBuffer[i] === 0xA0 && (i === 0 || iniContentBuffer[i - 1] !== 0xC2)) {
            estimatedSize++;
        }
    }

    // 创建一个新的缓冲区来存储替换后的内容
    const resultBuffer = Buffer.alloc(estimatedSize);
    let offset = 0;

    // 遍历 iniContentBuffer 中的每个字节
    for (let i = 0; i < iniContentBuffer.length; i++) {
        if (iniContentBuffer[i] === 0xA0 && (i === 0 || iniContentBuffer[i - 1] !== 0xC2)) {
            // 将单独的 A0 替换为 C2 A0
            resultBuffer[offset++] = 0xC2;
            resultBuffer[offset++] = 0xA0;
        } else {
            // 否则，直接复制字节
            resultBuffer[offset++] = iniContentBuffer[i];
        }
    }

    // 将替换后的缓冲区转换为字符串
    const iniContent = resultBuffer.toString('utf-8');

    const lines = iniContent.split('\n');
    const jsonArray = [];

    // 处理 INI 文件的每一行
    lines.forEach(line => {
        if (line.includes('=')) {
            const [key, ...valueParts] = line.split('=');
            const original = valueParts.join('=').trim(); // 处理可能包含等号的值
            jsonArray.push({
                key: key.trim(),
                original: original,
                translation: '',
                context: ''
            });
        }
    });

    // 保存为 JSON 文件
    const jsonContent = JSON.stringify(jsonArray, null, 2);
    fs.writeFileSync('global.json', jsonContent, { encoding: 'utf-8', flag: 'w' });
    console.log('INI 文件已转换为 JSON 并保存到 global.json');
}

// 获取所有文件 ID 列表，并按创建时间排序
async function fetchFileData() {
    const response = await axios.get('https://paratranz.cn/api/projects/8340/files', authHeader);
    const files = response.data;

    // 根据 createdAt 时间戳进行排序，时间越近的排在前面
    files.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));

    // 过滤掉 "folder" 为 "汉化规则" 的文件
    const filteredFiles = files.filter(file => file.folder !== "汉化规则");

    // 按顺序获取所有文件的翻译数据，并附加文件名
    const allData = await Promise.all(filteredFiles.map(async file => {
        const data = await fetchTranslationData(file.id);
        return data.map(item => ({ ...item, fileName: file.name, id: file.id })); // 附加文件名和 ID
    }));

    return allData;
}

// 根据文件 ID 获取翻译数据
async function fetchTranslationData(fileId) {
    const url = `https://paratranz.cn/api/projects/8340/files/${fileId}/translation`;
    const response = await axios.get(url, authHeader);
    return response.data;
}

// 合并 JSON 数据，优先保留 id 越大的数据
function mergeJsonData(allData) {
    const mergedData = {};
    let mergeOrder = 0;

    // 保持数据按创建时间从最新到最旧的排序（不再反转顺序）
    allData.forEach(dataList => {
        mergeOrder++;
        const currentFileName = dataList.fileName || `Unknown file ${mergeOrder}`;

        dataList.forEach(item => {
            const currentItem = mergedData[item.key];
            if (currentItem) {
                if (item.id > currentItem.id) {
                    // 如果当前项的 id 大于已存在项的 id，则替换并输出替换的文件名顺序
                    console.log(`Merge Order ${mergeOrder}: Key "${item.key}" replaced by data from ${currentFileName} (ID ${item.id} > ${currentItem.id})`);
                    mergedData[item.key] = item;
                } else {
                    // 输出跳过的替换信息
                    console.log(`Merge Order ${mergeOrder}: Key "${item.key}" from ${currentFileName} skipped (ID ${item.id} <= ${currentItem.id})`);
                }
            } else {
                mergedData[item.key] = item;
            }
        });
    });

    return Object.values(mergedData);
}

// 保存 global.json 中与 final.json 有差异的内容到 difference.json，忽略前后空格
function saveDifferences() {
    // 读取并解析 JSON 文件，移除 BOM
    const globalJson = JSON.parse(fs.readFileSync('global.json', 'utf-8'));
    const finalJson = JSON.parse(fs.readFileSync('final.json', 'utf-8'));

    const differences = globalJson.filter(gItem => {
        const fItem = finalJson.find(f => f.key === gItem.key);
        
        if (!fItem) {
            // 如果 final.json 中不存在对应的 key，则视为差异
            return true;
        }

        // 比较时移除空白符并处理换行符
        return gItem.original.trim().replace(/\s+/g, ' ') !== fItem.original.trim().replace(/\s+/g, ' ');
    });

    fs.writeFileSync('difference.json', JSON.stringify(differences, null, 2));
    console.log('global.json 中的差异已保存到 difference.json');
}

// 主函数
async function main() {
    try {
        convertIniToJson();

        const allData = await fetchFileData();

        // 合并所有数据
        const mergedData = mergeJsonData(allData);

        // 将合并结果保存为 final.json
        fs.writeFileSync('final.json', JSON.stringify(mergedData, null, 2));
        console.log('数据已合并并保存到 final.json');

        // 将 global.json 中的差异保存到 difference.json
        saveDifferences();
    } catch (error) {
        console.error('发生错误:', error);
    }
}

main();
