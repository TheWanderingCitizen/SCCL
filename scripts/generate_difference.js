const axios = require('axios');
const fs = require('fs');
const path = require('path');
const FormData = require('form-data');
const {
    buildDifferences,
    decodeIniBuffer,
    detectDifferenceMode,
    parseIniContent,
    partitionEncodingDamagedItems,
    toGlobalJsonItems
} = require('./difference_utils');

const PROJECT_ID = 8340;
const PARATRANZ_API_BASE = 'https://paratranz.cn/api';
const DEFAULT_TIME_ZONE = 'Asia/Hong_Kong';

// API 配置
const authHeader = {
    headers: {
        'Authorization': process.env.AUTHORIZATION
    }
};

// 支持以下两种版本号传入方式：
// node scripts/generate_difference.js "4.9.0 PTU 12218630"
// node scripts/generate_difference.js --version "4.9.0 PTU 12218630"
// 未传版本号时，使用香港时间生成 YYYY-MM-DD_HH-mm-ss 名称。
function getVersionArgument(args = process.argv.slice(2)) {
    const versionFlagIndex = args.indexOf('--version');
    let version = null;

    if (versionFlagIndex !== -1) {
        version = args[versionFlagIndex + 1];
    } else {
        const inlineVersion = args.find(arg => arg.startsWith('--version='));
        version = inlineVersion ? inlineVersion.slice('--version='.length) : args.find(arg => !arg.startsWith('-'));
    }

    if (!version) {
        return null;
    }

    version = version.trim();
    if (!version) {
        throw new Error('版本号不能为空');
    }

    // 禁止路径分隔符、Windows 非法文件名字符和控制字符，防止版本号逃逸当前目录。
    if (/[<>:"/\\|?*\x00-\x1f]/.test(version) || /[. ]$/.test(version)) {
        throw new Error(`版本号包含非法文件名字符: ${version}`);
    }

    return normalizeVersionName(version);
}

function normalizeVersionName(version) {
    const match = version.match(/^(.+)-(live|ptu)\.(\d+)$/i);
    return match ? `${match[1]} ${match[2].toUpperCase()} ${match[3]}` : version;
}

function getTimestampVersion(date = new Date()) {
    const parts = new Intl.DateTimeFormat('en-CA', {
        timeZone: DEFAULT_TIME_ZONE,
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hourCycle: 'h23'
    }).formatToParts(date);
    const values = Object.fromEntries(parts.map(({ type, value }) => [type, value]));
    return `${values.year}-${values.month}-${values.day}_${values.hour}-${values.minute}-${values.second}`;
}

function getDifferenceFileName(version, date = new Date()) {
    return `${version || getTimestampVersion(date)}.json`;
}

function setActionOutput(name, value) {
    if (process.env.GITHUB_OUTPUT) {
        fs.appendFileSync(process.env.GITHUB_OUTPUT, `${name}=${value}\n`, 'utf-8');
    }
}

// 将生成的差异文件上传并在 ParaTranz 项目中创建同名文件。
async function uploadDifferenceFile(filePath) {
    if (!process.env.AUTHORIZATION) {
        throw new Error('缺少 AUTHORIZATION 环境变量，无法上传到 ParaTranz');
    }

    const form = new FormData();
    const fileName = path.basename(filePath);
    const targetPath = process.env.PARATRANZ_PATH || '';
    form.append('file', fs.createReadStream(filePath), {
        filename: fileName,
        contentType: 'application/json'
    });
    // ParaTranz 新建文件接口要求同时提交 filename 和 path。
    form.append('filename', fileName);
    form.append('path', targetPath);

    const response = await axios.post(
        `${PARATRANZ_API_BASE}/projects/${PROJECT_ID}/files`,
        form,
        {
            headers: {
                ...form.getHeaders(),
                'Authorization': process.env.AUTHORIZATION
            },
            maxBodyLength: Infinity,
            maxContentLength: Infinity
        }
    );

    const uploadedFile = response.data && response.data.file;
    console.log(`已上传到 ParaTranz: ${uploadedFile ? uploadedFile.name : fileName}`);
    return response.data;
}

// 读取并转换 INI 文件为 JSON。英文原文和中文汉化文件都支持；
// 默认根据值中汉字占比识别，也可用 DIFFERENCE_MODE=source|translation 强制指定。
function convertIniToJson() {
    const iniContentBuffer = fs.readFileSync('global.ini');
    const decoded = decodeIniBuffer(iniContentBuffer);
    const parsedItems = parseIniContent(decoded.text);
    const partitioned = partitionEncodingDamagedItems(parsedItems);
    if (partitioned.validItems.length === 0) {
        throw new Error('global.ini 中没有可用于比较的有效条目');
    }

    const detected = detectDifferenceMode(partitioned.validItems, process.env.DIFFERENCE_MODE || 'auto');
    const jsonArray = toGlobalJsonItems(partitioned.validItems, detected.mode);

    // 保存为 JSON 文件
    const jsonContent = JSON.stringify(jsonArray, null, 2);
    fs.writeFileSync('global.json', jsonContent, { encoding: 'utf-8', flag: 'w' });
    const modeDescription = detected.mode === 'translation' ? '汉化差异' : '原文差异';
    const ratioDescription = detected.hanValueRatio === null
        ? '手动指定'
        : `含汉字值占比 ${(detected.hanValueRatio * 100).toFixed(2)}%`;
    console.log(`global.ini 编码: ${decoded.encoding}`);
    if (decoded.repairedOffsets.length > 0) {
        console.warn(
            `已按 Windows-1252 修复 ${decoded.repairedOffsets.length} 个孤立字节，`
            + `首个字节偏移: ${decoded.repairedOffsets.slice(0, 10).join(', ')}`
        );
    }
    if (partitioned.damagedItems.length > 0) {
        console.warn(
            `已跳过 ${partitioned.damagedItems.length} 条包含 U+FFFD 替换字符的受损文本，`
            + `避免产生假差异。key: ${partitioned.damagedItems.slice(0, 20).map(item => item.key).join(', ')}`
        );
    }
    console.log(`差异模式: ${modeDescription}（${ratioDescription}）`);
    console.log('INI 文件已转换为 JSON 并保存到 global.json');
    return detected.mode;
}

// 获取所有文件 ID 列表，并按创建时间排序
async function fetchFileData() {
    const response = await axios.get(`${PARATRANZ_API_BASE}/projects/${PROJECT_ID}/files`, authHeader);
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
    const url = `${PARATRANZ_API_BASE}/projects/${PROJECT_ID}/files/${fileId}/translation`;
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

// 保存 global.json 中与 final.json 有差异的内容。
function saveDifferences(outputFileName, differenceMode) {
    const globalJson = JSON.parse(fs.readFileSync('global.json', 'utf-8'));
    const finalJson = JSON.parse(fs.readFileSync('final.json', 'utf-8'));
    const result = buildDifferences(globalJson, finalJson, differenceMode);

    const outputPath = path.resolve(process.cwd(), outputFileName);
    fs.writeFileSync(outputPath, JSON.stringify(result.differences, null, 2), { encoding: 'utf-8', flag: 'w' });
    console.log(`已保存 ${result.differences.length} 条差异到 ${outputFileName}`);
    if (result.missingKeys.length > 0) {
        console.warn(
            `跳过 ${result.missingKeys.length} 个 ParaTranz 中不存在的汉化 key，`
            + `请先上传对应英文原文。示例: ${result.missingKeys.slice(0, 10).join(', ')}`
        );
    }
    return outputPath;
}

// 主函数
async function main() {
    try {
        const version = getVersionArgument();
        const differenceFileName = getDifferenceFileName(version);

        const differenceMode = convertIniToJson();

        const allData = await fetchFileData();

        // 合并所有数据
        const mergedData = mergeJsonData(allData);

        // 将合并结果保存为 final.json
        fs.writeFileSync('final.json', JSON.stringify(mergedData, null, 2));
        console.log('数据已合并并保存到 final.json');

        // 保存差异；传入版本号时，例如输出 "4.9.0 PTU 12218630.json"。
        const differenceFilePath = saveDifferences(differenceFileName, differenceMode);

        // 上传 difference 文件到 ParaTranz。
        await uploadDifferenceFile(differenceFilePath);
        setActionOutput('file_name', differenceFileName);
    } catch (error) {
        const apiDetails = error.response && error.response.data
            ? JSON.stringify(error.response.data)
            : error.message;
        console.error('发生错误:', apiDetails);
        process.exitCode = 1;
    }
}

main();
