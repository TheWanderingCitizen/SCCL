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
    console.log("Fetching files from Paratranz...");
    const response = await axios.get('https://paratranz.cn/api/projects/8340/files', authHeader);
    const files = response.data;

    console.log(`Fetched ${files.length} files.`);

    // 拼合后的数据和汉化规则
    const mergedData = {};
    const translationRules = {};
    const ruleFiles = [];
    let replace3dData = null;  // 用于存储 "汉化规则/3d替换.json" 的内容

    // 遍历文件，按需要进行处理
    for (const file of files) {
        console.log(`Processing file: ${file.name}`);
        const fileData = await fetchTranslationData(file.id);

        if (file.name === "汉化规则/3d替换.json") {
            console.log("Found 3d替换.json, processing...");
            replace3dData = {};
            fileData.forEach(item => {
                replace3dData[item.key] = item.translation;
            });
        } else if (file.folder === "汉化规则" && file.name !== "汉化规则/3d替换.json") {
            console.log(`Found 汉化规则 file: ${file.name}, processing...`);
            const rule = {};
            fileData.forEach(item => {
                rule[item.key] = item.translation;
            });
            translationRules[file.name] = rule;
            ruleFiles.push(file.name); 
        } else {
            console.log(`Merging data from file: ${file.name}`);
            fileData.forEach(item => {
                if (!mergedData[item.key] || item.id > mergedData[item.key].id) {
                    mergedData[item.key] = { translation: item.translation, id: item.id };
                } else if (!mergedData[item.key]) {
                    mergedData[item.key] = { translation: item.translation, id: item.id };
                }
            });
        }
    }

    return { mergedData, translationRules, ruleFiles, replace3dData };
}

// 根据文件 ID 获取翻译数据
async function fetchTranslationData(fileId) {
    const url = `https://paratranz.cn/api/projects/8340/files/${fileId}/translation`;
    console.log(`Fetching translation data for file ID: ${fileId}`);
    const response = await axios.get(url, authHeader);
    return response.data;
}

// 将拼合后的 JSON 和规则应用到 INI 格式，并在开头添加 BOM（EF BB BF）
function convertJsonToIni(jsonData, translationRules) {
    let iniContent = '\uFEFF'; // 添加 BOM (EF BB BF)

    // 获取键并按字母顺序排序
    const sortedKeys = Object.keys(jsonData).sort();

    sortedKeys.forEach(key => {
        let value = jsonData[key].translation;
        if (translationRules[key]) {
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
        console.log(`Created directory: ${dirname}`);
    }
}

// 主函数
async function main() {
    try {
        const { mergedData, translationRules, ruleFiles, replace3dData } = await fetchAndMergeTranslations();

        if (!replace3dData) {
            throw new Error("汉化规则/3d替换.json 未找到");
        }

        const outputDir = 'final_output';
        ensureDirectoryExistence(outputDir);

        for (const ruleFileName of ruleFiles) {
            console.log(`Generating INI file for rule: ${ruleFileName}`);
            const rules = translationRules[ruleFileName];

            // Combine 3D replace data with other rules
            const combinedRules = { ...replace3dData, ...rules };
            const iniContent = convertJsonToIni(mergedData, combinedRules);

            // Create a directory for each rule file
            const ruleDir = path.join(outputDir, ruleFileName.replace('汉化规则/', '').replace('.json', ''));
            ensureDirectoryExistence(ruleDir);

            // Save INI file in the corresponding directory
            const outputFileName = path.join(ruleDir, 'global.ini');
            ensureDirectoryExistence(outputFileName);

            fs.writeFileSync(outputFileName, iniContent, { encoding: 'utf-8' });
            console.log(`拼合后的翻译内容已转换为 INI 格式并保存到 ${outputFileName}`);
        }

        console.log("Generating global.ini with only 3d替换.json applied.");
        const finalIniContent = convertJsonToIni(mergedData, replace3dData);
        const finalOutputFileName = path.join(outputDir, 'global.ini');
        fs.writeFileSync(finalOutputFileName, finalIniContent, { encoding: 'utf-8' });
        console.log(`Generated global.ini and saved to ${finalOutputFileName}`);

    } catch (error) {
        console.error('发生错误:', error);
    }
}

main();
