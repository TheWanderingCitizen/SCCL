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

    // 拼合后的数据和汉化规则
    const mergedData = {};
    const translationRules = {};
    const ruleFiles = [];
    let replace3dData = null;  // 用于存储 "汉化规则/3d替换.json" 的内容

    // 遍历文件，按需要进行处理
    for (const file of files) {
        const fileData = await fetchTranslationData(file.id);

        if (file.name === "汉化规则/3d替换.json") {
            // 如果文件名为 "汉化规则/3d替换.json"，则提取其内容
            replace3dData = {};
            fileData.forEach(item => {
                replace3dData[item.key] = item.original;
            });
        } else if (file.folder === "汉化规则" && file.name !== "汉化规则/3d替换.json") {
            // 处理其他 "汉化规则" 文件，将其保存到 translationRules 中
            const rule = {};
            fileData.forEach(item => {
                rule[item.key] = item.original;
            });
            translationRules[file.name] = rule;  // 以文件名为 key 保存规则
            ruleFiles.push(file.name);  // 保存规则文件名
        } else {
            // 非 "汉化规则" 文件，正常合并
            fileData.forEach(item => {
                mergedData[item.key] = item.original;
            });
        }
    }

    return { mergedData, translationRules, ruleFiles, replace3dData };
}

// 根据文件 ID 获取翻译数据
async function fetchTranslationData(fileId) {
    const url = `https://paratranz.cn/api/projects/8340/files/${fileId}/translation`;
    const response = await axios.get(url, authHeader);
    return response.data;
}

// 将拼合后的 JSON 和规则应用到 INI 格式，并在开头添加 BOM（EF BB BF）
function convertJsonToIni(jsonData, translationRules) {
    let iniContent = '\uFEFF'; // 添加 BOM (EF BB BF)

    Object.keys(jsonData).forEach(key => {
        let value = jsonData[key];
        if (translationRules[key]) {
            // 如果存在汉化规则，应用规则进行替换
            value = translationRules[key];
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
    }
}

// 主函数
async function main() {
    try {
        const { mergedData, translationRules, ruleFiles, replace3dData } = await fetchAndMergeTranslations();

        // 确保已获取到 "汉化规则/3d替换.json" 的数据
        if (!replace3dData) {
            throw new Error("汉化规则/3d替换.json 未找到");
        }

        // 为每个汉化规则生成一个对应的 INI 文件
        for (const ruleFileName of ruleFiles) {
            const rules = translationRules[ruleFileName];

            // 将拼合后的 JSON 应用 "汉化规则/3d替换.json" 和当前的汉化规则生成 INI
            const combinedRules = { ...replace3dData, ...rules };
            const iniContent = convertJsonToIni(mergedData, combinedRules);

            // 构造输出文件路径
            const outputFileName = `final_output_${ruleFileName.replace('汉化规则/', '').replace('.json', '')}.ini`;
            ensureDirectoryExistence(outputFileName);

            // 将转换后的 INI 内容保存到文件
            fs.writeFileSync(outputFileName, iniContent, { encoding: 'utf-8' });
            console.log(`拼合后的翻译内容已转换为 INI 格式并保存到 ${outputFileName}`);
        }

    } catch (error) {
        console.error('发生错误:', error);
    }
}

main();