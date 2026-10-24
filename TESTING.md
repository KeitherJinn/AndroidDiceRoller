# 本地构建与测试

以下命令从项目根目录运行，使用本机已有的 Android SDK、JDK 和 Gradle 缓存。项目概况见 [README.md](README.md)。

## 工具链与离线前提

当前配置为 Android Gradle Plugin **9.4.1**、Android Lint **32.4.1**、Gradle Wrapper **9.7.1**，编译和目标 SDK 均为 **37**，设备最低为 Android **API 34**。`gradle/gradle-daemon-jvm.properties` 指定运行 Gradle 的 **JDK 25**；应用源码的 Java 兼容目标 **11** 是另一个设置，不应混淆。

离线运行前须具备对应的 Gradle 发行包、JDK、Android SDK 平台及构建工具，以及项目和 Lint 的依赖缓存。`local.properties` 是本机文件，不进入版本库；可由 Android Studio 按实际 SDK 位置生成，或使用已配置的 `ANDROID_HOME`。

**首次克隆且没有缓存时，不能直接按离线流程构建。** `--offline` 限制 Gradle 的依赖解析，但尚未缓存的 Gradle Wrapper 仍可能尝试下载其发行包。应先确认现有工具与缓存；确需补齐时再安排一次明确的联网准备。下面的参数同时禁止自动下载 JDK 和 Android SDK，不会替代缺失的本地工具。

## 构建、单元测试与完整 Lint

Windows PowerShell：

```powershell
.\gradlew.bat --offline '-Dorg.gradle.java.installations.auto-download=false' '-Pandroid.builder.sdkDownload=false' :app:assembleDebug :app:assembleQa :app:assembleRelease :app:assembleQaAndroidTest :app:testQaUnitTest
.\gradlew.bat --offline --no-configuration-cache '-Dorg.gradle.java.installations.auto-download=false' '-Pandroid.builder.sdkDownload=false' :app:lintDebug :app:lintQa :app:lintRelease
```

macOS / Linux：

```sh
./gradlew --offline -Dorg.gradle.java.installations.auto-download=false -Pandroid.builder.sdkDownload=false :app:assembleDebug :app:assembleQa :app:assembleRelease :app:assembleQaAndroidTest :app:testQaUnitTest
./gradlew --offline --no-configuration-cache -Dorg.gradle.java.installations.auto-download=false -Pandroid.builder.sdkDownload=false :app:lintDebug :app:lintQa :app:lintRelease
```

若 Unix 系统上的 Wrapper 没有执行权限，可用 `sh ./gradlew` 替代 `./gradlew`。当前 `testBuildType = "qa"` 对应的单元测试任务是 `testQaUnitTest`，设备测试目标也是 QA。

| 产物 | 路径 |
| --- | --- |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| QA 应用 APK | `app/build/outputs/apk/qa/app-qa.apk` |
| QA 测试 APK | `app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk` |
| Release APK | `app/build/outputs/apk/release/`；未配置发布签名时为未签名包 |
| 单元测试 HTML / XML | `app/build/reports/tests/testQaUnitTest/` / `app/build/test-results/testQaUnitTest/` |
| 各变体 Lint 报告 | `app/build/reports/lint-results-{debug,qa,release}.{html,xml,sarif}` |

Lint 包含测试源码与依赖检查，遇到错误会失败；没有 baseline、禁用规则列表、`checkOnly` 缩窄范围或忽略警告设置。需要显式运行三个变体，不能用默认 `lint` 任务的单个结果代表全部变体。

## 设备测试与数据边界

普通 Debug / Release 应用标识为 `com.example.diceroller`；QA 应用为 **`com.example.diceroller.uitest`**，测试包为 `com.example.diceroller.uitest.test`。UI 测试在清数据前会断言目标包名。

- QA UI 用例会清空 **QA 历史**，并在 QA 私有目录模拟文件损坏与写入失败。不要把需保留的记录放在 QA 安装中。
- 配色测试只修改 QA 的 `appearance` 偏好，并恢复原值。普通应用的历史与偏好不应被修改。
- 应用私有数据隔离不等于整个设备隔离：复制测试会使用系统剪贴板；旋转测试会临时改变设备方向，并在 `finally` 中恢复和验证原角度与自动旋转设置。因此应选已有的专用模拟器或测试设备，运行期间不要并行操作其方向设置。

先启动已有设备并查看 `adb devices -l`，选择状态为 `device` 的实际序列号。下面不假定某个 `emulator-...` 已存在，也不会创建模拟器、下载系统镜像或安装正常应用包。

Windows PowerShell（默认使用当前用户的常见 SDK 安装位置；自定义位置可通过 `ANDROID_HOME` 指定）：

```powershell
$testSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$testAdb = Join-Path $testSdk 'platform-tools\adb.exe'
& $testAdb devices -l
$testDevice = Read-Host '输入状态为 device 的专用测试设备序列号'
if ([string]::IsNullOrWhiteSpace($testDevice)) { throw '未选择测试设备' }
$deviceState = & $testAdb -s $testDevice get-state
if ($LASTEXITCODE -ne 0 -or "$deviceState".Trim() -ne 'device') { throw '设备未就绪' }
& $testAdb -s $testDevice install -r app/build/outputs/apk/qa/app-qa.apk
if ($LASTEXITCODE -ne 0) { throw 'QA 应用安装失败' }
& $testAdb -s $testDevice install -r app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk
if ($LASTEXITCODE -ne 0) { throw 'QA 测试包安装失败' }
& $testAdb -s $testDevice shell am instrument -w -r com.example.diceroller.uitest.test/androidx.test.runner.AndroidJUnitRunner
```

macOS / Linux（先把现有 SDK 的 `platform-tools` 加入 `PATH`）：

```sh
adb devices -l
printf '输入状态为 device 的专用测试设备序列号: '
IFS= read -r testDevice
if [ -n "$testDevice" ] && [ "$(adb -s "$testDevice" get-state)" = device ]; then
  adb -s "$testDevice" install -r app/build/outputs/apk/qa/app-qa.apk &&
  adb -s "$testDevice" install -r app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk &&
  adb -s "$testDevice" shell am instrument -w -r com.example.diceroller.uitest.test/androidx.test.runner.AndroidJUnitRunner
else
  printf '未选择可用的测试设备。\n'
fi
```

以测试运行器末尾的 `OK (N tests)` 或失败汇总判定结果，不能仅凭 ADB 进程退出码判断。保留完整输出以便复核实际运行数量和跳过情况。这里直接使用已有 ADB 和 AndroidJUnitRunner，**不需要 Gradle 的 UTP 设备测试组件**；`connectedQaAndroidTest` 是另一条执行路径，离线时可能因缺少 UTP 缓存失败。

## 用例覆盖

- **计算与批次**：表达式边界、全角输入、次数和工作量限制、整组取高/低、Long 运算、完整批次统计与保留策略。
- **状态与存储**：保存失败后提示持续存在、重试、清空失败、取消不发布半批次、新 ViewModel 恢复草稿；旧格式迁移、损坏或未知版本保护、恢复前原字节备份。
- **输入与界面**：超长输入及旧值回送不能改变投掷含义；快捷骰子、批次和单次复用、完整复制、明细分页、键盘上方按钮、清空确认。
- **真实旋转**：使用 `UiAutomation.setRotation` 旋转设备，检查横竖布局下的历史锚点、旧完成提示不重播，并验证原旋转设置恢复。新批次的同类完成提示仍须正常出现。
- **配色**：全部选项可达；采样实际按钮颜色；重建与偏好读取恢复；未知字符串及错误类型安全回退；清历史保留配色；固定浅深主题的 64 组文字对比度检查。
- **中英文表达式**：两种语言的帮助和错误文案均保留完整的 `d20`、`2d6+3`、`4d8-2` 示例，并逐个验证可解析，防止排版替换运算符后仅解析出有效前缀。

人工检查可补充中英文、深色、放大字体、窄窗口与横屏。键盘打开时，保存/恢复警告和操作按钮仍应可用。历史 v2 保存完整批次；v1 的每条记录迁移为一个独立单次批次，不猜测旧分组。恢复备份位于应用私有 `history-backups` 目录，清空当前历史不会删除这些备份。

## 仓库整理验证（2026-10-23）

从 Git 暂存内容导出干净源码副本，不携带原工程的 `local.properties`、构建缓存或个人 IDE 配置；通过 `ANDROID_HOME` 使用现有 SDK，并使用本机 JDK 和依赖缓存完成离线验证：

- Debug、QA、Release 及 QA 测试 APK 构建成功，共执行 155 个任务。
- 40 项 QA 单元测试通过，无失败、错误或跳过。
- 本次未启动模拟器、未重跑设备测试或三个变体的完整 Lint；Release 构建附带的 `lintVitalRelease` 不能代替完整 Lint。
- 编译输出保留两处 Compose 测试接口弃用提示；此次仓库整理没有改变测试调度方式。

本机日志位于被忽略的 `build/repository-preparation/`，不会随源码提交。文档和忽略规则变更没有改变应用逻辑。

## 此前的 1.2.1 完整验证记录（2026-10-23）

以下为仓库整理前的完整回归结果。设备测试和完整 Lint 没有在上述整理验证中重复运行，不应把这些历史记录当作新一次执行结果。

| 检查 | 上轮结果 |
| --- | --- |
| 单元测试 | 40 项通过 |
| 设备测试 | 38 项通过 |
| Debug 完整 Lint | 0 错误、2 警告 |
| QA 完整 Lint（含测试源码） | 0 错误、5 警告 |
| Release 完整 Lint | 0 错误、2 警告 |

三个变体的警告按问题与位置去重后为 **5 处**，不是 5 种规则：

| 保留项 | 数量 | 理由 |
| --- | --- | --- |
| Kotlin Compose 插件 `NewerVersionAvailable` | 1 | 更新提示，不是构建错误。插件升级需要连同 Kotlin / AGP 编译链验证，不能仅为清除提示独立跨版本升级。 |
| `kotlinx-coroutines-test-jvm` 的 `NewerVersionAvailable` | 1 | 测试库与生产使用的协程 Core／Android 均为 1.9.0。新版测试库会引入新版运行库；仅升级测试会使运行环境不一致，应将运行库与测试库一并验证。 |
| `ThemeUiTest` 的 `UseKtx` | 3 | 用例显式检查 `SharedPreferences.Editor.commit()` 的布尔返回值，验证同步落盘成功和原值恢复。KTX `edit(commit = true)` 返回 `Unit`；直接替换会丢失失败检查，仅检查内存读回也不能替代落盘验证。 |

上述保留项没有被抑制或关闭。历史上的受限 API、锁定 Activity 方向和表达式排版问题已经修复，不属于这 5 处保留项。版本更新提示可能随仓库信息变化，应以实际本轮报告为准，不把上轮提示的可用版本当作永远最新。
