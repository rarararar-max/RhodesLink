# 新电脑配置（最简版）

> 本项目的签名密钥 `release-key.jks` **已经放在 GitHub 仓库里**，所以换电脑**不需要带任何密钥文件**。
> 你只需要记住**一个密码**。

## 你唯一要记住的

**签名密码：`rhodes2026`**

> 这个密码不在仓库里（写在本地 `local.properties`），只有你知道。**请务必记牢**，或写进密码管理器/纸上存一份。

## 新电脑步骤（4 步）

### 1. 安装环境
- 装 JDK 21 + Android Studio（含 Android SDK 35）

### 2. 拉取代码
```bash
git clone https://github.com/rarararar-max/RhodesLink.git
cd RhodesLink
```

### 3. 配置 local.properties
项目根目录新建 `local.properties`，写上：
```
KEYSTORE_PASSWORD=rhodes2026
```
> `sdk.dir` 那行不用手写，Android Studio 打开项目会自动补上。

### 4. 打包
```bash
set "JAVA_HOME=D:\Program Files\Android\Android Studio\jbr"
gradlew assembleRelease
```
看到 `BUILD SUCCESSFUL` 即成功，APK 在 `app\build\outputs\apk\release\`。

## 日常迭代

改代码 → 递版本号（`app/build.gradle.kts` 里 `versionCode`/`versionName`）→ 打包 → 提交推送：
```bash
git add .
git commit -m "版本说明"
git push
```

## 一句话总结

> **换电脑 = clone 代码 + 写一行密码 + 打包。** 密钥在仓库里，不用带；密码 `rhodes2026` 要记住。

---

*说明：本项目公开密钥，作者不介意代码被使用/仿制；密钥仅用于保证老用户能正常升级。*
