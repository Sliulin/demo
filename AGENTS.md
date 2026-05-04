# AGENTS.md

## 项目概览

这是一个单模块 Android 项目，根项目名为 `Demo`，应用模块为 `:app`。技术栈为 Kotlin、Jetpack Compose、Material 3、Navigation Compose、Lifecycle ViewModel、Kotlinx Serialization、Coroutines/Flow，以及 Android NSD/本地 TCP 通信用于局域网联机。

应用定位是局域网联机桌游辅助 App：房主设备作为 Host，本地服务发现房间并同步游戏状态；其他玩家作为 Client 加入房间。App 负责房间发现、玩家状态、阶段流转、公开事件结算、聊天/消息同步和 UI 展示，部分战斗或数值判定由线下玩家完成后再录入。

## 目录结构

- `settings.gradle.kts`：Gradle 项目声明，当前只包含 `:app`。
- `build.gradle.kts`：根级插件声明。
- `gradle/libs.versions.toml`：Android Gradle Plugin、Kotlin、Compose BOM 和 AndroidX 依赖版本。
- `app/build.gradle.kts`：应用模块配置，`namespace`/`applicationId` 为 `com.example.demo`，开启 Compose。
- `app/src/main/AndroidManifest.xml`：应用入口与网络权限，允许明文流量。
- `app/src/main/java/com/example/demo/MainActivity.kt`：Compose 入口与导航。
- `app/src/main/java/com/example/demo/viewmodel/GameViewModel.kt`：主要状态管理、房间/游戏流程、网络消息处理。
- `app/src/main/java/com/example/demo/engine/GameRuleEngine.kt`：游戏规则与事件结算逻辑。
- `app/src/main/java/com/example/demo/model/`：可序列化数据模型和枚举。
- `app/src/main/java/com/example/demo/network/`：NSD、Host/Client、网络消息协议。
- `app/src/main/java/com/example/demo/ui/`：Compose UI 组件、页面和主题。
- `text/`：需求、页面划分、高保真原型等产品资料。

不要把 `app/build/`、`.gradle/`、`.idea/`、`.kotlin/` 中的生成文件或 IDE 缓存当成源码修改目标，除非用户明确要求。

## 常用命令

在 Windows/PowerShell 中从项目根目录运行：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

说明：

- `:app:assembleDebug` 用于验证编译和生成 Debug APK。
- `:app:testDebugUnitTest` 用于本地 JVM 单元测试。
- `:app:connectedDebugAndroidTest` 需要已连接设备或模拟器。
- 如果 Gradle 需要下载依赖而当前环境无网络或被沙箱拦截，先向用户说明并请求授权，不要绕过审批。

## 代码约定

- 使用 idiomatic Kotlin，遵循 `kotlin.code.style=official`。
- 保持项目架构整洁，避免把无关职责堆在同一个文件中；如有必要，可以新建文件承载新的页面、组件、模型、规则或 helper。
- UI 使用 Jetpack Compose 和 Material 3，新增页面优先放在 `ui/<feature>/` 下。
- 状态管理走 ViewModel + `StateFlow`/`MutableStateFlow`。不要引入 `LiveData`。
- Compose 组件尽量保持无业务逻辑：状态向下传递，事件通过 lambda 向上传递。
- 游戏规则优先放在 `GameRuleEngine` 或明确的 domain/helper 中，不要散落在 Composable 内。
- 网络协议消息放在 `network/NetworkMessage.kt`，需要跨端同步的数据模型必须保持 `@Serializable`。
- 共享状态字段优先加到 `GameUiState`，并通过不可变 `copy()` 更新。
- 处理网络、NSD、Socket、协程时，注意生命周期清理，避免在 `ViewModel.onCleared()` 后继续持有资源。
- 新增字符串如果是用户可见文案，优先考虑放入 `res/values/strings.xml`；已有代码中存在直接中文文案时，可按当前局部风格小范围延续。

## 架构注意事项

- Host 侧是权威状态来源，负责玩家列表、事件队列、阶段推进和广播。
- Client 侧应通过网络消息同步状态，不要自行推导会影响全局一致性的结果。
- `GameRuleEngine` 应保持纯逻辑，避免依赖 Android Context、UI 状态或网络对象。
- 阶段流转围绕 `GamePhase.PHASE_1` 和 `GamePhase.PHASE_2` 展开；改动时同步检查导航、事件队列、投票确认和重开逻辑。
- 玩家存活状态、灵脉、锦囊、天道庇护等字段会影响结算，修改模型时要检查序列化消息和 UI 展示。

## 测试与验证

- 修改规则结算、模型或 ViewModel 流程后，至少运行 `.\gradlew.bat :app:testDebugUnitTest`。
- 修改 Compose/UI、Manifest、依赖或资源后，至少运行 `.\gradlew.bat :app:assembleDebug`。
- 修改联网、NSD、Socket、设备权限相关逻辑后，需要在真机或模拟器环境做手动验证；仅本地单测不能覆盖局域网发现和设备连接行为。
- 当前仓库包含示例单元测试和仪器测试骨架，新增核心规则时优先补充 JVM 单元测试。

## 文件操作限制

禁止批量删除文件或目录。不要使用：

- `del /s`
- `rd /s`
- `rmdir/s`
- `Remove-Item -Recurse`
- `rm -rf`

需要删除文件时，只能一次删除一个明确路径的文件，例如：

```powershell
Remove-Item "C:\path\to\file.txt"
```

如果需要批量删除文件或目录，应停止操作并请求用户手动删除。

## 协作注意事项

- 当前目录不一定是 Git 仓库；执行 Git 操作前先确认是否存在 `.git`。
- 不要修改 `local.properties` 中的本机 SDK 路径，除非用户明确要求。
- 不要无关升级 AGP、Kotlin、Compose BOM 或 AndroidX 版本；依赖升级需要说明原因并验证构建。
- 保持改动聚焦：修 UI 时不要顺手重写规则，修规则时不要顺手重做页面样式。

## 编译执行约定

- 永远不要主动运行编译、测试或构建命令，包括但不限于 `.\gradlew.bat :app:assembleDebug`、`.\gradlew.bat :app:testDebugUnitTest`、`.\gradlew.bat :app:connectedDebugAndroidTest`。
- 即使修改了规则、模型、ViewModel、Compose/UI、Manifest、依赖、资源、联网、NSD、Socket 或权限相关逻辑，也不要自动编译或测试；只做代码级检查、静态检查或改动范围说明。
- 最终回复中如果涉及验证，应明确说明未运行编译/测试，由用户自行编译验证。

## 环境命令限制

- 不要使用 `rg` / `ripgrep`。当前环境中的 `rg.exe` 来自 Codex 应用安装目录，执行时会被系统拒绝访问；搜索文件或文本时改用 PowerShell 原生命令，例如 `Get-ChildItem`、`Select-String`。

## 编码与中文文案保护

- 源码、资源、Gradle 配置和文档文件统一使用 UTF-8 编码保存；不要用系统默认 ANSI/GBK 编码重写包含中文的文件。
- 修改包含中文注释或 UI 文案的文件时，优先使用补丁方式做小范围改动，避免用 `Set-Content`、`Out-File`、重定向或脚本把整个文件重新写出。
- 如果确实必须用 PowerShell 写文件，必须显式指定 UTF-8 编码，并在写入后检查中文内容没有变成乱码。
- 发现文件已有乱码时，不要继续扩大改动范围；先定位最近一次改动并只修复受影响的中文文案。

## 前端 UI 文案约定

- 所有会显示在前端 UI 中的用户可见文案必须使用中文，包括按钮、标题、标签、提示语、弹窗、占位文本、状态标识、调试面板文案和无障碍描述。
- 不要在前端 UI 中新增英文文案；仅代码内部标识、路由名、变量名、日志 tag、协议字段、测试名称等非用户可见内容可以使用英文。
- 如果因为编码问题临时使用了英文占位，必须在同一次修改中改回中文后再交付。
