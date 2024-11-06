# 使用说明

1. 去[汉化仓库](https://github.com/TheWanderingCitizen/LocalizationData)点击fork sync
3. 设置SW_PUBLISH的值(参考[开关](#开关))
4. 执行action

## 环境变量配置
secret变量名参考merge_translate.yml
### Github相关
去[github token settings](https://github.com/settings/tokens)创建token
- **GITHUB_FORK_USERNAME**：${fork仓库所属用户}
- **GITHUB_FORK_REPO**：${fork仓库名}
- **GITHUB_TOKEN**：${你的github token，注意要有pr权限}

### paratranz相关
- **PZ_PROJECT_ID**：${paratranz的project id}
- **PZ_TOKEN**：${paratranz的api token}

### Cloudflare R2相关
去[cf api token](https://dash.cloudflare.com/?to：/:account/r2/api-tokens)创建token
- **S3_ACCESS_KEY**：${r2的s3 access key}
- **S3_SECRET_KEY**：${r2的s3 secret key}
- **S3_BUCKET**：${r2的bucket名}
- **S3_ENDPOINT**：${r2的ENDPOINT}

### 开关
- **SW_PUBLISH**： ${true/false} 推送（push仓库，pr，cdn）的总开关，不设置默认关闭，当不确定输出内容是否正确时，可以关闭此开关来只输出文件，下载核对无误后再开启

## 执行流程

1. 从fork仓库拉取各个分支到不同目录，n个分支拉取n次
2. 读取paratranz翻译结果，根据sccl仓库global.ini文件取对应key,合并翻译条目,若合并后条目数量不一致会报错
3. 修改不同分支的文件，提交到fork仓库并提pr给源仓库以及上传到cdn

## 注意事项
1. **SW_PUBLISH**默认关闭
2. 当paratranz获取到的文件最新版本号为PTU时，除full以外的其它版本不会推送，且full版本推送到存储桶的目录变为ptu
3. 版本排序逻辑：比较**主版本号**（3.24.2），若相同则比较**副版本号**（9374678）， 若相同则比较**paratranz文件id**
4. 合并文件时仍然与原来脚本的逻辑一致（除了汉化规则文件夹以外的文件都会被合并）
5. 文件输出路径为final_output,该目录下的子目录与仓库目录一致（就是各个分支对应的文件）
