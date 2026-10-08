# 洛天依陪跑 · 安卓原生版

基于 [w8888-hh/luotianyi-running-app](https://github.com/w8888-hh/luotianyi-running-app) 的安卓原生实现，源码位于本仓库 `android-native/`。使用 Kotlin、Jetpack Compose、Room、DataStore 和 Media3，不依赖 WebView 或 Capacitor。

[下载 v0.1.3 测试 APK](https://github.com/lvwei114/luotianyi-running-app/releases/tag/v0.1.3-native-test) · [改进说明](docs/v0.1.2改进说明.md) · [功能清单](docs/功能迁移清单.md)

## 功能

- 引导跑、跟随跑、自定义 60–220 BPM，以及独立 5–60 分钟定时跑；暂停不计时。
- 独立跑步界面突出距离与运动时间，支持收起与返回；记录页展示累计分析、本周趋势、连续天数、心率曲线及删除确认。
- GPS_PROVIDER 记录距离；网络定位和步幅估算不会写入记录。平均步频按有效运动时间计算，步幅使用 GPS 有效路段及对应步数；缺少测量显示 `--`。
- 运动前台服务支持 GPS 预热、计步、暂停/恢复、锁屏通知、断点备份恢复、心率提醒、补水和鼓励语；保存失败可重试。
- 批量导入本地音乐、新建或追加歌单、BPM 检测、顺序/随机/心率智能播放、喜欢与移除/恢复、MediaSession 锁屏控制，以及保存本次播放歌单。
- 标准 BLE 心率扫描与连接、断开恢复、十秒过期处理；Tanaka/经典/自定义最大心率及静息心率储备法。
- 内置图库、多图自选背景、头像、本地语音与 TTS 回退、节拍器、四通道音量、设置备份及旧版 JSON 迁移。
- 固定在屏幕上方的“天依说”浮窗，原生光晕、粒子及页面动画按完整/简洁/省电档位启用；蓝色强调色为 `#66CCFF`。

## 构建

支持 Android 8.0（API 26）及以上；compileSdk/targetSdk 为 36。安装 Android Studio，准备 JDK 17 或更新版本、Android SDK Platform 36，通过 Android Studio 打开 `android-native/` 并完成 Gradle 同步。本项目开发验证使用 JDK 21。

命令行构建时，将 `JAVA_HOME` 设置为 JDK 路径、`ANDROID_HOME` 设置为 SDK 路径；也可在本目录的本地 `local.properties` 中设置 `sdk.dir`。个人 SDK 路径不要提交。

Windows PowerShell：

```powershell
cd android-native
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
```

Linux/macOS：

```sh
cd android-native
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug --console=plain
./gradlew :app:assembleDebug --console=plain
```

构建产物为 `app/build/outputs/apk/debug/app-debug.apk`。源码仓库不包含 APK、构建缓存、个人 SDK 路径或签名密钥；现成安装包通过 GitHub Releases 分发。

## 验证与版本

版本 `0.1.3-native-test`，versionCode 4，应用名“洛天依陪跑”。32 项 JVM/Robolectric 测试通过，Lint 0 错误、11 条提示；检查了 390×844 和 360×640 界面截图。APK 中文名称、SHA256 与 v2 调试签名校验通过。

2026-10-08，维护者反馈已完成真机验证，未发现问题。这是维护者的验证反馈；未提供机型、系统版本及逐项测试记录。测试包使用调试签名。

运动数据与个人信息保存在本机。备份 JSON 不包含音频和图片，迁移时需重新导入媒体。[历史 bug 修复说明](docs/bugs修复说明.md)。

## 来源与许可

原项目作者：Cory（哔哩哔哩：本人内心十分开朗）；代码采用 [MIT License](LICENSE)。保留原项目作者、许可证及资源来源说明。

原生版作者：绿卫。

- 语音模型：[xdedmmc2/GPT-SoVITS-Luo-Tianyi](https://github.com/xdedmmc2/GPT-SoVITS-Luo-Tianyi)，模型作者：只想干饭的麦克斯韦妖。
- 内置语音和背景来源于原项目及原项目图库；当前桌面图标使用维护者提供的 `IMG_20261009_002429.png` 图片。
- 洛天依声音与形象版权归上海禾念信息科技有限公司所有；代码 MIT 许可证不改变这些资源的原有权利归属。
