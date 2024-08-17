const axios = require('axios');
const fs = require('fs');
const path = require('path');

// API 配置
const authHeader = {
    headers: {
        'Authorization': process.env.AUTHORIZATION
    }
};

// 从 Paratranz 获取所有文件并拼合为一个 JSON 对象，同时处理 "汉化规则" 文件和 "汉化规则/3d替换.json"
async function fetchAndMergeTranslations() {
    const response = await axios.get('https://paratranz.cn/api/projects/8340/files', authHeader);
    const files = response.data;

    // 拼合后的数据
    const mergedData = {};
    let replace3dData = null;  // 用于存储 "汉化规则/3d替换.json" 的内容

    // 遍历文件，按需要进行处理
    for (const file of files) {
        const fileData = await fetchTranslationData(file.id);

        if (file.name === "汉化规则/3d替换.json") {
            console.log("Found 3d替换.json, processing...");
            // 如果文件名为 "汉化规则/3d替换.json"，则提取其内容
            replace3dData = {};
            fileData.forEach(item => {
                replace3dData[item.key] = item.original;
            });
        } else {
            console.log(`Merging data from file: ${file.name}`);
            // 非 "汉化规则" 文件，正常合并
            fileData.forEach(item => {
                mergedData[item.key] = item.original;
            });
        }
    }

    return { mergedData, replace3dData };
}

// 根据文件 ID 获取翻译数据
async function fetchTranslationData(fileId) {
    const url = `https://paratranz.cn/api/projects/8340/files/${fileId}/translation`;
    console.log(`Fetching translation data for file ID: ${fileId}`);
    const response = await axios.get(url, authHeader);
    return response.data;
}

// 将拼合后的 JSON 和 "3d替换.json" 应用到 INI 格式，并在开头添加 BOM（EF BB BF）
function convertJsonToIni(jsonData, replace3dData) {
    let iniContent = '\uFEFF'; // 添加 BOM (EF BB BF)

    Object.keys(jsonData).forEach(key => {
        let value = jsonData[key];
        if (replace3dData && replace3dData[key]) {
            // 如果存在 3d替换规则，应用规则进行替换
            value = replace3dData[key];
        }
        iniContent += `${key}=${value}\n`;
    });

    return iniContent;
}

// 确保目录存在
function ensureDirectoryExistence(filePath) {
    const dirname = path.dirname(filePath);
    if (!fs.existsSync(dirname)) {
        fs.mkdirSync(dirname, { recursive: true });
        console.log(`Created directory: ${dirname}`);
    }
}

// 主函数
async function main() {
    try {
        const { mergedData, replace3dData } = await fetchAndMergeTranslations();

        // 确保已获取到 "汉化规则/3d替换.json" 的数据
        if (!replace3dData) {
            throw new Error("汉化规则/3d替换.json 未找到");
        }

        console.log("Generating final INI file with 3d替换.json applied...");

        // 将拼合后的 JSON 应用 "汉化规则/3d替换.json" 生成最终的 INI 文件
        const iniContent = convertJsonToIni(mergedData, replace3dData);

        // 构造输出文件路径
        const outputFileName = `final_output.ini`;
        ensureDirectoryExistence(outputFileName);

        // 将转换后的 INI 内容保存到文件
        fs.writeFileSync(outputFileName, iniContent, { encoding: 'utf-8' });
        console.log(`Final INI file has been generated and saved to ${outputFileName}`);

    } catch (error) {
        console.error('发生错误:', error);
    }
}

main();
