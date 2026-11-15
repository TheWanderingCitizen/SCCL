# 使用说明

1. 设置相关api所需的环境变量
2. 设置SW_PUBLISH的值(参考[开关](#开关))
3. 执行action

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

### 开关v

- **SW_PUBLISH**： ${true/false} 推送（push仓库，pr，cdn）的总开关，不设置默认关闭，当不确定输出内容是否正确时，可以关闭此开关来只输出文件，下载核对无误后再开启

## 执行流程

1. 从sc汉化盒子仓库拉取所需分支到不同目录
2. 按 `SW_PROFILE` 找到该通道最新版本，合并当前通道截至该版本的文件，以及版本严格更早的其他通道文件（按数字主版本号、构建号比较）；保留草稿译文，再根据 sccl 仓库 global.ini 取对应 key，若合并后条目数量不一致会报错
3. 修改不同分支的文件，提交到fork仓库并提pr给源仓库以及上传到cdn

## 注意事项

1. **SW_PUBLISH**默认关闭
2. `SW_PROFILE` 指定发布与合并通道（LIVE / PTU，默认 PTU），full 汉化的 PTU 通道上传至存储桶 ptu 目录
3. 合并版本上限先比较**主版本号**（3.24.2），再比较**构建号**（9374678）；同通道同版本同构建号以文件 ID 选择最新版本标签。同版本同构建号的其他通道文件不纳入
4. 合并跳过“汉化规则”和无法识别版本的文件名；没有当前通道的有效版本文件时停止。不同文件的重复 key 仍保留词条 ID 较大的记录
5. 文件输出路径为final_output,该目录下的子目录与仓库目录一致（就是各个分支对应的文件）

Java 发布、翻译检查和差异生成均直接分页读取 ParaTranz 词条，不按状态过滤，包含有译文的草稿（`stage=0`）。Java 合并时仅在译文为空白时回退到英文原文。切换后首次运行会重新拉取全部文件，之后继续按文件更新时间复用缓存；缓存元信息仅在全部拉取成功后更新。

Java 发布按 `SW_PROFILE`（`LIVE` / `PTU`）筛选参与合并的文件：先找到当前通道的最新版本，纳入当前通道截至该版本的文件，以及版本严格更早的其他通道文件。版本先比较数字主版本号（如 `4.10.0`），再比较构建号；同版本同构建号的其他通道文件不纳入。只识别 `4.10.0 LIVE 12519617.json` 这类版本文件名（支持子目录及 EPTU），跳过“汉化规则”和无法识别版本的文件；找不到当前通道版本时停止。重复 key 仍保留已纳入文件中词条 ID 较大的记录，草稿译文照常参与。LIVE/PTU 发布管线已分别指定通道，不发布管线可手动选择通道，默认 PTU。缓存仍可共享全部通道数据，合并时只读取筛选后的文件。
