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

    // 按顺序获取所有文件的翻译数据
    const allData = await Promise.all(filteredFiles.map(file => fetchTranslationData(file.id)));
    return allData;
}

// 根据文件 ID 获取翻译数据
async function fetchTranslationData(fileId) {
    const url = `https://paratranz.cn/api/projects/8340/files/${fileId}/translation`;
    const response = await axios.get(url, authHeader);
    return response.data;
}

// 合并 JSON 数据，优先保留后面的数据
function mergeJsonData(allData) {
    const mergedData = {};

    allData.forEach(dataList => {
        dataList.forEach(item => {
            mergedData[item.key] = item;
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
        // 比较时移除空白符并处理换行符
        return fItem && gItem.original.trim().replace(/\s+/g, ' ') !== fItem.original.trim().replace(/\s+/g, ' ');
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
