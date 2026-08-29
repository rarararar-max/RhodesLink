# 🏥 罗德岛通讯端 (Rhodes Terminal)

> 一款以 AI 角色互动为核心的 Android 聊天应用 —— 创建角色、长期陪伴、剧情创作、群像互动。

<!-- 📸 截图位置①（顶部横幅，建议聊天主界面竖版截图，宽度约 800px）：
![罗德岛通讯端主界面](docs/screenshots/main.png) -->

罗德岛通讯端是一款面向普通用户的 AI 角色聊天软件：你可以为角色设定性格与背景，和它进行长期聊天；也可以建立群聊、制作 Galgame 剧情、给角色挂载世界观知识库，并随时把角色卡和聊天记录导出、跨设备迁移。

> **重要说明**：AI 的回复来自你自行配置的第三方模型服务（不同模型的表达、速度、稳定性与收费方式不同）；本应用负责保存角色、聊天记录与相关设置，**不内置任何模型**。

## ✨ 功能特性

- 💬 **私聊 & 群聊**：与单个角色或多个角色的群像互动
  <!-- 📸 截图位置②a：![群聊界面](docs/screenshots/group_chat.png) -->
- 🧠 **记忆系统**：长期记忆 + 向量记忆，帮助角色找回久远经历
  <!-- 📸 截图位置②b：![记忆设置](docs/screenshots/memory.png) -->
- 📚 **知识库 / 世界书**：给角色挂载设定资料与世界观
- 🎮 **Galgame & 小游戏**：制作互动剧情，内置麻将等玩法
  <!-- 📸 截图位置②c：![Galgame剧情](docs/screenshots/galgame.png) -->
- 🤖 **自动化互动**：每日内容推送、群聊自动聊天、定时回复
- 📅 **内容创作**：日记、朋友圈、礼物、排行、关系系统
- 💾 **数据管理**：完整备份与恢复、角色卡导出分享
- 📞 **语音通话**、通知提醒

## 🛠 技术栈

- Kotlin Multiplatform + Compose Multiplatform
- SQLDelight（本地数据库）· Ktor Client（网络）
- Koin（DI）· Voyager（导航）· Coil（图片）· WorkManager（后台任务）

## 🏗 构建

支持 **Docker / GitHub Actions / 本地命令行** 三种构建方式，产出 `arm64`、`x86_64`、`universal` 三种架构的 APK。详细步骤见 [BUILD.md](BUILD.md)。

环境要求：JDK 21 · Android SDK 35 · Gradle 8.10.2（Docker 构建只需 Docker 20.10+）

```bash
# 本地构建所有架构
./scripts/build-apk.sh all

# 或使用 Docker
docker compose run --rm build-all
```

## 📖 用户手册

完整使用说明见 [docs/罗德岛通讯端用户使用说明书.txt](docs/罗德岛通讯端用户使用说明书.txt)。

## 📂 项目结构

| 目录/文件 | 说明 |
|-----------|------|
| `app/` | Android 应用模块（Compose UI、导航、业务界面） |
| `shared/` | KMP 共享模块（网络、数据库、状态管理） |
| `docs/` | 用户手册等文档 |
| `scripts/` | 构建脚本 |

## 📄 许可证

本项目采用**自定义许可协议**：**非商业用途免费使用（需保留版权声明并署名），商业用途需获得作者书面授权**。详见 [LICENSE](LICENSE)。

## ⚠️ 免责声明

本项目仅提供聊天客户端功能，不包含任何 AI 模型服务；生成内容由所配置的第三方模型提供，本项目不对生成内容负责。
