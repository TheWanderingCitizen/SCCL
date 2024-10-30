const axios = require('axios');
const fs = require('fs');
const path = require('path');

// API configuration
const authHeader = {
    headers: {
        'Authorization': process.env.AUTHORIZATION
    }
};

// Fetch all files from Paratranz and merge into one JSON object,
// also process "3d替换.json" in "汉化规则" folder
async function fetchAndMergeTranslations() {
    console.log("Fetching files from Paratranz...");
    const response = await axios.get('https://paratranz.cn/api/projects/8340/files', authHeader);
    const files = response.data;

    console.log(`Fetched ${files.length} files.`);

    // Merged data and translation rules
    const mergedData = {};
    const translationRules = {};
    const ruleFiles = [];
    let replace3dData = {};  // Initialize to empty object

    // Process files
    for (const file of files) {
        console.log(`Processing file: ${file.name} in folder: ${file.folder}`);
        const fileData = await fetchTranslationData(file.id);

        if (file.folder === "汉化规则" && file.name === "3d替换.json") {
            console.log("Found 3d替换.json, processing...");
            fileData.forEach(item => {
                replace3dData[item.key] = item.translation;
            });
        } else if (file.folder === "汉化规则" && file.name !== "3d替换.json") {
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
                }
            });
        }
    }

    return { mergedData, translationRules, ruleFiles, replace3dData };
}

// Fetch translation data by file ID
async function fetchTranslationData(fileId) {
    try {
        const url = `https://paratranz.cn/api/projects/8340/files/${fileId}/translation`;
        console.log(`Fetching translation data for file ID: ${fileId}`);
        const response = await axios.get(url, authHeader);
        return response.data;
    } catch (error) {
        console.error(`Failed to fetch translation data for file ID: ${fileId}`, error);
        throw error;
    }
}

// Convert merged JSON and rules to INI format, add BOM at the beginning
function convertJsonToIni(jsonData, translationRules) {
    let iniContent = '\uFEFF'; // Add BOM (EF BB BF)

    // Get keys and sort alphabetically
    const sortedKeys = Object.keys(jsonData).sort();

    sortedKeys.forEach(key => {
        let value = jsonData[key].translation;
        if (translationRules && translationRules[key]) {
            value = translationRules[key];
        }
        iniContent += `${key}=${value}\n`;
    });

    return iniContent;
}

// Ensure directory exists
function ensureDirectoryExistence(targetPath) {
    const dirPath = path.extname(targetPath) ? path.dirname(targetPath) : targetPath;
    if (!fs.existsSync(dirPath)) {
        fs.mkdirSync(dirPath, { recursive: true });
        console.log(`Created directory: ${dirPath}`);
    }
}

// Main function
async function main() {
    try {
        const { mergedData, translationRules, ruleFiles, replace3dData } = await fetchAndMergeTranslations();

        const outputDir = 'final_output';
        ensureDirectoryExistence(outputDir);

        // Generate an INI file for each translation rule
        for (const ruleFileName of ruleFiles) {
            console.log(`Generating INI file for rule: ${ruleFileName}`);
            const rules = translationRules[ruleFileName];

            // Combine 3D replace data with other rules if replace3dData exists
            const combinedRules = { ...replace3dData, ...rules };
            const iniContent = convertJsonToIni(mergedData, combinedRules);

            // Save INI file directly in the output directory with a unique name
            const iniFileName = ruleFileName.replace('.json', '.ini');
            const outputFileName = path.join(outputDir, iniFileName);
            fs.writeFileSync(outputFileName, iniContent, { encoding: 'utf-8' });
            console.log(`Merged translation content has been converted to INI format and saved to ${outputFileName}`);
        }

        // Generate an INI file applying only "3d替换.json" if it exists
        if (Object.keys(replace3dData).length > 0) {
            console.log("Generating 3d替换.ini with only 3d替换.json applied.");
            const finalIniContent = convertJsonToIni(mergedData, replace3dData);
            const finalOutputFileName = path.join(outputDir, '3d替换.ini');
            fs.writeFileSync(finalOutputFileName, finalIniContent, { encoding: 'utf-8' });
            console.log(`Generated 3d替换.ini and saved to ${finalOutputFileName}`);
        }

        // Generate an INI file applying no rules (base translation)
        console.log("Generating base.ini with no rules applied.");
        const baseIniContent = convertJsonToIni(mergedData, {});
        const baseOutputFileName = path.join(outputDir, 'base.ini');
        fs.writeFileSync(baseOutputFileName, baseIniContent, { encoding: 'utf-8' });
        console.log(`Generated base.ini and saved to ${baseOutputFileName}`);

    } catch (error) {
        console.error('An error occurred:', error);
    }
}

main();
