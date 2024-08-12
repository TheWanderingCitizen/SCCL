const axios = require('axios');
const fs = require('fs');
const ini = require('ini');

// API 配置
const authHeader = {
    headers: {
        'Authorization': '5c672e8056d802c52d3398e1376174f0',
        'Cookie': 'sid=s%3A5c672e8056d802c52d3398e1376174f0.Nvn4B0oVX%2BeUAIP7cSqwkqp7%2BIr8DwL1AhXhpRKs7r8'
    }
};

// 读取并转换 INI 文件为 JSON
function convertIniToJson() {
    const iniContent = fs.readFileSync('global.ini', 'utf-8');
    const iniData = ini.parse(iniContent);
    const jsonArray = [];

    for (const key in iniData) {
        if (iniData.hasOwnProperty(key)) {
            jsonArray.push({
                key: key,
                original: iniData[key],
                translation: '',
                context: ''
            });
        }
    }

    fs.writeFileSync('global.json', JSON.stringify(jsonArray, null, 2));
    console.log('INI 文件已转换为 JSON 并保存到 global.json');
}

// 获取所有文件 ID 列表
async function fetchFileIds() {
    const response = await axios.get('https://paratranz.cn/api/projects/8340/files', authHeader);
    return response.data.map(file => file.id);
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

// 比较 global.json 和 final.json 的差异
function compareJsonFiles() {
    const globalJson = JSON.parse(fs.readFileSync('global.json', 'utf-8'));
    const finalJson = JSON.parse(fs.readFileSync('final.json', 'utf-8'));
    
    const differences = finalJson.filter(fItem => {
        const gItem = globalJson.find(g => g.key === fItem.key);
        return gItem && gItem.original !== fItem.original;
    });

    fs.writeFileSync('difference.json', JSON.stringify(differences, null, 2));
    console.log('差异已保存到 difference.json');
}

// 主函数
async function main() {
    try {
        convertIniToJson();

        const fileIds = await fetchFileIds();

        // 并行获取所有文件的翻译数据
        const allDataPromises = fileIds.map(fetchTranslationData);
        const allData = await Promise.all(allDataPromises);

        // 合并所有数据
        const mergedData = mergeJsonData(allData);

        // 将合并结果保存为 final.json
        fs.writeFileSync('final.json', JSON.stringify(mergedData, null, 2));
        console.log('数据已合并并保存到 final.json');

        // 比较 global.json 和 final.json
        compareJsonFiles();
    } catch (error) {
        console.error('发生错误:', error);
    }
}

main();
