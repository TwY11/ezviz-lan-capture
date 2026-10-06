# 萤石局域网采集 · EZVIZ LAN Capture

Android 局域网音视频采集应用。当前版本 **v3.0**，固定使用萤石 SDK **5.32**，通过设备本地账号登录，获取远程摄像头画面及声音。

[下载 APK](https://github.com/TwY11/ezviz-lan-capture/releases/latest) · [版本记录](CHANGELOG.md)

## 主要功能

- 实时局域网预览、设备发现及手动 IP 登录。
- 自定义单次采集：默认 **8 帧/秒、32 kHz、2 秒**，目标 16 张 JPG，输出一个重采样后的单声道 PCM16 WAV。
- 普通采集只保存图片和输出 WAV，不保留原始码流及诊断文件。
- 独立保留 **10 秒音画对照**：固定 8 帧/秒、32 kHz，生成图片、音频、声谱图及离线 `review.html`。
- 只导出本次登录的 ZIP；**下一次登录成功会删除应用内旧会话**。已经手动导出的文件不受影响。
- AppKey 与设备密码只保存在内存；不设置 AccessToken，不调用云端设备查询或云端抓图接口，不申请手机摄像头或麦克风权限。

这不是情绪模型或 MQTT 应用，目前专注于局域网采集。它也不包含后台常驻服务；离开页面会停止采集，连接失败需手动处理。

## 使用

1. 将 Android 设备与摄像头连接到可相互访问的同一局域网。
2. 打开“设备连接”，输入自己应用的真实 AppKey 并初始化 SDK。
3. 搜索摄像头，或填写设备 IP、服务端口、本地账号与密码，点击“登录并播放”。
4. 点击“采集设置”，保存配置后点击“开始采集”。一次点击只采集一段。
5. 点击“导出本次登录 ZIP”。10 秒对照结果须完整解压，再用浏览器打开其中的 `review.html`。

配置范围：1～30 帧/秒；单段 1～60 秒；最多 600 张目标图片。音频输出可选 8、16、22.05、24、32、44.1、48 kHz。高帧率不保证设备能够达到；不补抓，不补静音，失败或中断目录会标记 `incomplete-`。

## 兼容性与限制

- Android 7.0（API 24）及以上，当前构建仅包含 **arm64-v8a**。
- 包名 `com.face.emotion.lanprobe.recovered`，显示名称“萤石局域网采集”。
- 原始音频解析仅适用于已验证的 IMKH、RTP PT104、单 AU AAC-LC / 16 kHz / 单声道组合；不是通用所有摄像头的协议实现，动态 PT104 本身不保证代表 AAC。
- 抓图调用时间与音频起点没有经过严格的共同媒体时基验证。10 秒页面用于近似人工对照，不声称精确同步。
- 时长参数表示采集窗口。保留完整 AAC 帧会产生几十毫秒时长差异，例如 2 秒窗口可能输出 2.048 秒 WAV；当前不会强制裁切成固定样本数。
- SDK 初始化和局域网使用的许可、套餐要求及商业授权应以萤石官方条款为准。无 AccessToken 的技术路径不等于获得免费商用或再分发授权。

## 构建

环境：Android SDK Platform 36、AGP 9.2.1、Gradle 9.4.1（Wrapper 已包含）。本版本使用 JDK 25 完成验证；SDK 依赖首次构建时从 Maven 下载。

在 Android Studio 中打开本目录，配置 Android SDK，或在未跟踪的 `local.properties` 中设置自己的 `sdk.dir`。不要提交本地路径、凭据或签名私钥。

```powershell
.\gradlew.bat :lanprobe:assembleDebug :lanprobe:testDebugUnitTest :lanprobe:lintDebug
```

macOS/Linux 使用 `./gradlew`。输出：`lanprobe/build/outputs/apk/debug/lanprobe-debug.apk`。

```powershell
adb install -r .\lanprobe\build\outputs\apk\debug\lanprobe-debug.apk
```

Release 提供的 v3.0 APK 沿用原应用的 Debug 签名，以便原用户覆盖安装；**不是应用商店正式发布签名**。其他电脑自行构建会使用自己的 Debug 密钥，可能不能直接覆盖已安装的下载版。请勿为解决签名冲突而直接卸载仍有未导出数据的应用。

## 测试

- 当前 24 项 JVM 单元测试覆盖配置边界、调度、RTP 解析和重采样。
- 独立 Android 测试使用合成的 1 kHz AAC 音，不包含摄像头录音；测试素材不打包进主 APK。

```powershell
.\gradlew.bat :lanprobe:assembleDebugAndroidTest
adb install -r .\lanprobe\build\outputs\apk\androidTest\debug\lanprobe-debug-androidTest.apk
adb shell am instrument -w com.face.emotion.lanprobe.recovered.test/com.face.emotion.lanprobe.CaptureInstrumentation
```

上述测试会结束主应用进程，需重新输入凭据。它验证音频格式、文件保留、异常处理及导出，不替代真实摄像头抓图验证。

## 后续迭代

1. 创建功能分支：`git switch -c feature/your-change`。
2. 修改源码，运行测试；安装后完成实际摄像头验证。
3. 更新 `lanprobe/build.gradle.kts` 中的 `versionCode` / `versionName` 和 `CHANGELOG.md`。
4. 提交并推送，合并到 `main`。
5. 给对应提交创建版本标签，并在 GitHub Releases 上传对应 APK。不要把 APK 放进 Git 历史，也不要提交签名私钥。

## 目录

- `lanprobe/src/main/`：界面、局域网播放、采集、音频处理及导出。
- `lanprobe/src/test/`：JVM 单元测试。
- `lanprobe/src/androidTest/`：独立设备测试及合成测试音。
- `gradle/`：依赖版本和 Wrapper。

公开仓库不包含本地 `recovery/`、采集音视频、历史证据、备份、AppKey、设备密码或签名密钥。第三方 SDK 和依赖保留各自权利与许可；公开可见不自动授予源码的开源再许可，本仓库尚未选择独立的开源许可证。
