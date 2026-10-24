# DiceRoller

基于 **Kotlin、Jetpack Compose 和 Material 3** 的 Android 骰子应用。当前版本为 **1.2.1**（`versionCode = 4`），支持 Android 14 及以上设备。

## 功能

- 输入 `d20`、`2d6+3`、`4d8-2` 等表达式，或快捷选择常见骰型；支持重复投掷、取高／取低模式及备注。
- 按完整批次展示总和、均值、最小值和最大值；分页查看骰值，复制完整结果，载入整批或单次参数。
- 本地保存历史；兼容旧格式，损坏数据恢复前先备份原文件。配色偏好独立保存。
- 提供系统动态配色及四种固定配色，深浅模式跟随系统；支持中文、英文、宽窄布局和大字体。

## 构建环境

| 项目 | 当前配置 |
| --- | --- |
| Gradle Wrapper | 9.7.1 |
| Android Gradle Plugin | 9.4.1 |
| Kotlin／Compose 编译器插件 | 2.2.10 |
| Compose BOM | 2026.09.00 |
| Gradle daemon 使用的 JDK | 25 |
| Java 源码与目标字节码级别 | 11 |
| Android 编译／目标 SDK | 37 |
| 最低 Android SDK | 34（Android 14） |

**JDK 25 用来运行构建工具，Java 11 是应用模块的编译目标，两者含义不同。** 项目的 daemon 条件记录在 [`gradle/gradle-daemon-jvm.properties`](gradle/gradle-daemon-jvm.properties)；应用运行的最低 Android 版本由 `minSdk` 决定。

准备可被 Gradle 检测到的 JDK 25、Android SDK Platform 37 及所需 Build Tools。可以在支持 AGP 9.4 的 Android Studio 中打开仓库根目录并配置 SDK；仅使用命令行时，可将 `ANDROID_HOME` 指向已有 SDK，或在本地 `local.properties` 中设置 `sdk.dir`。本机 SDK 路径不要提交到仓库。

## 构建与安装

在仓库根目录使用随项目提供的 Wrapper，无需另装全局 Gradle。Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug
```

macOS／Linux 将命令开头替换为 `./gradlew`。首次运行可能需要联网获取 Gradle 分发包、构建插件、依赖或缺失的工具链；已有工具和缓存会被复用。

如果本机已准备好 **JDK、SDK、Wrapper 分发包及所有依赖缓存**，可以限制构建使用现有组件：

```powershell
.\gradlew.bat --offline '-Dorg.gradle.java.installations.auto-download=false' '-Pandroid.builder.sdkDownload=false' :app:assembleDebug
```

离线参数不会补齐缺失组件；Wrapper 自身首次启动所需的 Gradle 分发包也必须预先具备。

生成的调试安装包位于 `app/build/outputs/apk/debug/app-debug.apk`。连接已授权的 Android 设备或启动现有模拟器后，可使用 `:app:installDebug` 安装，或从 Android Studio 运行应用。

## 测试

普通应用标识为 `com.example.diceroller`。设备测试使用独立的 `qa` 变体，安装标识为 `com.example.diceroller.uitest`，避免测试清空普通应用历史。

单元测试、三个构建变体的 Lint、设备测试命令及验证边界统一记录在 [`TESTING.md`](TESTING.md)。测试生成的报告位于 `app/build/`，不作为源文件提交。

## 代码结构

| 位置 | 内容 |
| --- | --- |
| `app/src/main/java/com/example/diceroller/` | Kotlin 源码；入口、界面组件、ViewModel、骰子引擎、批次模型、历史存储与复制格式化 |
| `app/src/main/java/com/example/diceroller/ui/theme/` | 配色、主题、选择器及偏好存储 |
| `app/src/main/res/` | 英中字符串、图标、主题与 Android 资源 |
| `app/src/main/AndroidManifest.xml` | 应用声明 |
| `app/src/test/` | 主机单元测试 |
| `app/src/androidTest/` | 设备界面、存储及本地化测试 |
| `gradle/`、根目录及 `app/` 下的 Gradle 配置 | Wrapper、版本目录、工具链条件及构建设置 |

`src/main/java/` 下使用 `.kt` 文件是当前源码布局；目录名称不表示项目使用 Java 编写。界面通过状态与回调连接 ViewModel，计算和存储依赖可注入，便于独立验证失败与恢复路径。

## 仓库内容

保留源码、资源、测试、文档、构建配置，以及 Wrapper 脚本和 `gradle-wrapper.jar`。`gradle-daemon-jvm.properties` 虽由工具生成，仍属于可共享的构建配置。

构建产物、缓存、本机 SDK 路径、个人 IDE 状态和签名凭据不入仓库；以 [`.gitignore`](.gitignore) 为准。需要交付 APK 或测试报告时，可另行作为发布附件提供。

仓库尚未提供 `LICENSE`，项目许可范围待维护者明确。
