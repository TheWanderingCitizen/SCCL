# StarCitizen Chinese Localization Kit

中文百科汉化组使用的《星际公民》汉化差异对比与版本发布工具。

使用说明见 [SCCL Wiki](https://github.com/TheWanderingCitizen/SCCL/wiki)。

## 目录结构

```text
SCCL/
├─ .github/workflows/       # GitHub Actions 自动化
├─ data/                    # ParaTranz 词条和游戏配置数据
├─ java/                    # Java/Maven 发布工具
├─ scripts/                 # Node.js 和 Python 工具脚本
├─ 规则配置/                # 翻译、匹配、拼音和地点覆盖规则
├─ global.ini               # 手动上传与发布流程的固定入口
└─ README.md
```

## 脚本

- `scripts/generate_difference.js`：将 `global.ini` 转换并生成差异 JSON，然后上传到 ParaTranz。
- `scripts/merge_and_check.py`：合并 ParaTranz 数据并检查翻译键值，报告输出到 `reports/translation-check/`。

`global.ini` 保留在根目录，不应移入 `data/`：GitHub Actions 的 push 监听、Worker 同步和 Java 发布流程都以该路径为稳定接口。

Worker 上传的 `worker-inbox/*.ini` 是一次性输入：Action 将其同步到 `global.ini` 后，会在同一次提交中删除该收件箱文件。如果该文件的 Git blob hash 已经出现在 `global.ini` 历史中但不是当前版本，Action 会将其视为过期 Worker 输入并丢弃，从而保护手动上传的新版本。
