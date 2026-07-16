const axios = require('axios');
const fs = require('fs');
const path = require('path');
const FormData = require('form-data');

const PROJECT_ID = 8340;
const PARATRANZ_API_BASE = 'https://paratranz.cn/api';

// API 配置
const authHeader = {
    headers: {
        'Authorization': process.env.AUTHORIZATION
    }
};

// 支持以下两种版本号传入方式：
// node script.js "4.9.0 PTU 12218630"
// node script.js --version "4.9.0 PTU 12218630"
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

    return version;
}

function getDifferenceFileName(version) {
    return version ? `${version}.json` : 'difference.json';
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

// 保存 global.json 中与 final.json 有差异的内容到 difference.json，忽略前后空格
function saveDifferences(outputFileName) {
    // 读取并解析 JSON 文件，移除 BOM
    const globalJson = JSON.parse(fs.readFileSync('global.json', 'utf-8'));
    const finalJson = JSON.parse(fs.readFileSync('final.json', 'utf-8'));

    const finalByKey = new Map(finalJson.map(item => [item.key, item]));
    const differences = globalJson.filter(gItem => {
        const fItem = finalByKey.get(gItem.key);
        
        if (!fItem) {
            // 如果 final.json 中不存在对应的 key，则视为差异
            return true;
        }

        // 比较时移除空白符并处理换行符
        return gItem.original.trim().replace(/\s+/g, ' ') !== fItem.original.trim().replace(/\s+/g, ' ');
    });

    const outputPath = path.resolve(process.cwd(), outputFileName);
    fs.writeFileSync(outputPath, JSON.stringify(differences, null, 2), { encoding: 'utf-8', flag: 'w' });
    console.log(`global.json 中的差异已保存到 ${outputFileName}`);
    return outputPath;
}

// 主函数
async function main() {
    try {
        const version = getVersionArgument();
        const differenceFileName = getDifferenceFileName(version);

        convertIniToJson();

        const allData = await fetchFileData();

        // 合并所有数据
        const mergedData = mergeJsonData(allData);

        // 将合并结果保存为 final.json
        fs.writeFileSync('final.json', JSON.stringify(mergedData, null, 2));
        console.log('数据已合并并保存到 final.json');

        // 保存差异；传入版本号时，例如输出 "4.9.0 PTU 12218630.json"。
        const differenceFilePath = saveDifferences(differenceFileName);

        // 上传 difference 文件到 ParaTranz。
        await uploadDifferenceFile(differenceFilePath);
    } catch (error) {
        const apiDetails = error.response && error.response.data
            ? JSON.stringify(error.response.data)
            : error.message;
        console.error('发生错误:', apiDetails);
        process.exitCode = 1;
    }
}

main();
