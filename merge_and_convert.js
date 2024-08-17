const axios = require('axios');
const fs = require('fs');

// API 配置
const authHeader = {
    headers: {
        'Authorization': process.env.AUTHORIZATION
    }
};

// 从 Paratranz 获取所有文件并拼合为一个 JSON 对象，同时处理 "汉化规则" 文件
async function fetchAndMergeTranslations() {
    const response = await axios.get('https://paratranz.cn/api/projects/8340/files', authHeader);
    const files = response.data;

    // 拼合后的数据和汉化规则
    const mergedData = {};
    const translationRules = {};
    const ruleFiles = [];

    // 遍历文件，按需要进行处理
    for (const file of files) {
        const fileData = await fetchTranslationData(file.id);
        if (file.folder === "汉化规则") {
            // 处理 "汉化规则" 文件，将其保存到 translationRules 中
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

    return { mergedData, translationRules, ruleFiles };
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

// 主函数
async function main() {
    try {
        const { mergedData, translationRules, ruleFiles } = await fetchAndMergeTranslations();

        // 从本地加载 3d替换.json
        const replace3dData = JSON.parse(fs.readFileSync('3d替换.json', 'utf-8'));

        // 为每个汉化规则生成一个对应的 INI 文件
        for (const ruleFileName of ruleFiles) {
            const rules = translationRules[ruleFileName];

            // 将拼合后的 JSON 和 3d替换.json 以及当前的汉化规则组合起来生成 INI
            const combinedRules = { ...replace3dData, ...rules };
            const iniContent = convertJsonToIni(mergedData, combinedRules);

            // 将转换后的 INI 内容保存到文件
            const outputFileName = `final_output_${ruleFileName.replace('.json', '')}.ini`;
            fs.writeFileSync(outputFileName, iniContent, { encoding: 'utf-8' });
            console.log(`拼合后的翻译内容已转换为 INI 格式并保存到 ${outputFileName}`);
        }

    } catch (error) {
        console.error('发生错误:', error);
    }
}

main();
