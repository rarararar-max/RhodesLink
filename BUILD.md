# APK 构建指南

本项目支持两种构建方式：Docker 本地构建和 GitHub Actions 远程构建。

## 构建产物

每次构建会生成三种架构的 APK：

| 文件名 | 架构 | 说明 |
|--------|------|------|
| `app-arm64-v8a-release.apk` | ARM64 | 适用于大多数现代 Android 设备 |
| `app-x86_64-release.apk` | x86_64 | 适用于模拟器和部分 Chromebook |
| `app-universal-release.apk` | Universal | 包含所有架构，体积较大 |

## 方式一：Docker 本地构建

### 前提条件

- 安装 [Docker](https://docs.docker.com/get-docker/)
- 安装 [Docker Compose](https://docs.docker.com/compose/install/)

### 构建步骤

**构建所有架构：**

```bash
docker compose run --rm build-all
```

**构建指定架构：**

```bash
# 构建 arm64
docker compose run --rm build-arm64

# 构建 amd64
docker compose run --rm build-amd64

# 构建 universal
docker compose run --rm build-universal
```

构建完成后，APK 文件会生成在 `apk-output/` 目录下。

### 首次构建

首次构建需要下载 Docker 镜像和依赖，可能需要较长时间。后续构建会利用缓存，速度会快很多。

## 方式二：GitHub Actions 远程构建

### 前提条件

- 代码已推送到 GitHub 仓库

### 构建步骤

1. 打开 GitHub 仓库页面
2. 点击 `Actions` 标签
3. 选择 `Build Android APK` workflow
4. 点击 `Run workflow`
5. 选择参数：
   - `build_type`: 选择要构建的架构（all/arm64/amd64/universal）
   - `build_variant`: 选择构建变体（release/debug）
6. 点击 `Run workflow` 按钮

构建完成后，在 workflow 的 `Artifacts` 部分下载 APK 文件。

## 本地命令行构建（不使用 Docker）

如果不想使用 Docker，也可以直接在本地构建：

```bash
# 赋予执行权限
chmod +x gradlew

# 构建所有架构
./scripts/build-apk.sh all

# 构建指定架构
./scripts/build-apk.sh arm64
./scripts/build-apk.sh amd64
./scripts/build-apk.sh universal
```

## 环境要求

### Docker 构建

- Docker 20.10+
- Docker Compose 2.0+
- 磁盘空间：约 10GB（首次构建）

### 本地构建

- JDK 21
- Android SDK 35
- Gradle 8.10.2（通过 wrapper 自动下载）

## 故障排除

### Docker 构建失败

1. 检查 Docker 是否正常运行：`docker info`
2. 清理 Docker 缓存：`docker system prune`
3. 重新构建镜像：`docker compose build --no-cache`

### GitHub Actions 构建失败

1. 检查 workflow 日志中的错误信息
2. 确保 Gradle 配置正确
3. 检查是否有依赖下载失败

### APK 安装失败

1. 确保设备架构与 APK 架构匹配
2. 检查 Android 版本是否满足最低要求（Android 8.0+）
3. 启用"未知来源"安装权限

## 文件说明

| 文件 | 说明 |
|------|------|
| `Dockerfile` | Docker 构建环境配置 |
| `docker-compose.yml` | Docker Compose 编排配置 |
| `scripts/build-apk.sh` | APK 构建脚本 |
| `.github/workflows/build-apk.yml` | GitHub Actions workflow |
