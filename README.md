# BatMUD CN Android

BatMUD 的 Android 原生客户端 + 实时翻译引擎。在手机上玩 BatMUD，英文内容通过百度大模型 API 实时翻译为中文，完整保留 ANSI 色彩，横屏布局完整呈现 80 列终端内容。

> PC 版入口：[BatMUD-CN](https://github.com/stupidpig-heng/BatMUD-CN) — Python Web 客户端，浏览器中玩。

---

## 目录

- [功能特性](#功能特性)
- [效果预览](#效果预览)
- [架构](#架构)
- [快速开始](#快速开始)
- [翻译策略](#翻译策略)
- [角色状态 HUD](#角色状态-hud)
- [ANSI 颜色保留](#ansi-颜色保留)
- [缓存系统](#缓存系统)
- [设置说明](#设置说明)
- [构建](#构建)
- [项目结构](#项目结构)

---

## 功能特性

- **实时翻译** — 百度大模型 API（LLM），英文游戏文本实时翻译为中文
- **流式逐行输出** — 翻译完一行立刻显示一行，文字持续流入，无"卡住→突然出现"的等待感
- **ANSI 色彩完整保留** — 16 色终端调色板，物品/出口/对话颜色与服务端一致
- **智能翻译策略** — 默认白色文本仅显示中文，彩色关键词显示 `English(中文)`，短指令/ASCII 地图不翻译
- **角色状态固定 HUD** — HP/SP/EP/Exp 固定在界面顶部，不参与终端滚动，带彩色标签和迷你血量条
- **可靠的自动滚屏** — 流式输出不干扰自动滚底，手动上滑暂停，滑回底部自动恢复
- **80 列终端适配** — 横屏锁定完整呈现终端内容，ASCII 地图不折行不错位
- **等宽字体** — 全局使用系统等宽字体，确保字符画、数字列严格对齐
- **两级翻译缓存** — 内存 LRU + Room SQLite 磁盘持久化，重启后缓存仍在
- **命令历史** — 支持方向键翻找历史命令

---

## 效果预览

服务端原始输出：

```
You are walking on a path. Type [绿色]look[/绿色] to view the
current room. [蓝色粗体]Ghost of Pypo: hello[/蓝色粗体]
```

翻译后 APK 显示：

> 你正走在一条小路上。输入 **[绿色]look(看)[/绿色]** 来查看当前房间。
> **[蓝色粗体]Ghost of Pypo: hello（皮波的幽灵：你好）[/蓝色粗体]**

---

## 架构

```text
┌─────────────────────────────┐
│         GameScreen          │  ← Jetpack Compose UI
│  StatusBar / HUD / Terminal │
│       CommandInput          │
└──────────┬──────────────────┘
           │ StateFlow
┌──────────▼──────────────────┐
│      GameViewModel          │  ← 状态管理 + 设置持久化
└──────────┬──────────────────┘
           │
┌──────────▼──────────────────┐
│       GameEngine            │  ← Telnet 连接 + 行处理
│  TelnetClient  LineProcessor│
└──────────┬──────────────────┘
           │
┌──────────▼──────────────────┐
│        Translator           │  ← 百度 API + 两级缓存
│  BaiduApiClient   LruCache  │
│  Room (SQLite 磁盘缓存)     │
└──────────┬──────────────────┘
           │ HTTPS
    百度翻译 API
```

**数据流：**

```text
BatMUD Server (Telnet)
       │
       ▼
[TelnetClient]  ← 协商 IAC、过滤 MCCP
       │
       ▼
[LineProcessor.feed(onLine)]  ← 字节流 → 按行解析
       │                            │
       ├── [mergeParsedLines]       │  流式回调：
       │      硬折行合并            │  每行翻译完立刻
       │      跨 chunk 缓冲         │  onLine() → UI 更新
       │                            │
       ▼                            │
[processLine()]                     │
  ├── ANSI 段拆分                  │
  ├── 逐段翻译（API/缓存）         │
  ├── 默认白色 → 仅中文            │
  ├── 彩色文本 → English(中文)     │
  └── 短指令/地图 → 原样透传       │
       │                            │
       ▼                            ▼
[AnsiRenderer]              [addOutputLine]
  ANSI → AnnotatedString      StateFlow 更新
       │                            │
       ▼                            ▼
[Prompt?] ── HUD（固定）    Compose 重组渲染
  普通文本 ── 终端（滚动 + snapshotFlow 自动滚底）
```

---

## 快速开始

### 方式一：直接安装 APK（推荐）

1. 下载 `app-debug.apk`
2. 访问 [百度翻译开放平台](https://fanyi-api.baidu.com/) 注册并开通 **「大模型文本翻译API」**（免费）
3. 安装 APK，首次启动进入设置页 → 填入百度 APP ID 和 Secret Key
4. 返回游戏页面，自动连接 BatMUD

### 方式二：从源码构建

**环境要求：** Android Studio Hedgehog+ / JDK 17 / Android SDK 35

```bash
# 设置环境变量
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-21

# 构建
./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

---

## 翻译策略

### 按 ANSI 颜色段智能处理

与 PC 版完全一致的逐段翻译逻辑：

| 文本类型 | 判定条件 | 显示格式 | 示例 |
|----------|----------|----------|------|
| 默认白色文本 | `fg=37`, 无样式 | **仅中文** | `你身处北门守卫室。` |
| 彩色关键词 | 有色且 >2 字符 | **English(中文)** | `Iron portcullis (closed)(铁制吊闸(关闭))` |
| 短指令 | ≤2 字符 | 原样透传 | `n`, `s`, `i` |
| ASCII 地图/字符画 | 字母占比 <40% | 原样透传 | `HHH   #-` |
| 状态提示行 | Prompt 检测 | 路由到 HUD | `Hp:318/318 Sp:25/25 ...` |

### 硬折行合并

MUD 服务端 80 列自动换行会截断句子。客户端自动检测并合并：

- **ANSI 续行** — 前一行颜色未关闭 + 下一行以 ANSI+空格开头
- **缩进续行** — 下行以空格缩进开头
- **跨 chunk 缓冲** — TCP 分片导致不完整行自动缓冲到下一数据块
- **ANSI 边界清理** — 合并时剥离 `\x1b[0m` + `\x1b[SGR]m` 重设/重开对

合并后整句翻译，避免 `instructions there.` 被独立翻译导致语义断裂。

---

## 角色状态 HUD

HP/SP/EP/Exp 从终端文本流中提取，固定在界面顶部，不参与滚动。

```text
┌─────────────────────────────────────┐
│ 🟢 已连接  batmud.bat.org       ☰  │  ← 连接状态
├─────────────────────────────────────┤
│ HP 318/318 ████  SP 25/25 ████     │  ← 固定 HUD
│ EP 183/183 ████  EXP 356  TNL 2400 │     (不滚动)
├─────────────────────────────────────┤
│ 你身处多特尔沃尔的北门守卫室。      │  ← 终端（滚动）
│ ...                                 │
│ >                                   │  ← 命令输入
└─────────────────────────────────────┘
```

| 标签 | 颜色 | 血量条阈值 |
|------|------|-----------|
| **HP** | 🟢 绿 | >60% 绿 / 30-60% 橙 / <30% 红 |
| **SP** | 🔵 蓝 | >60% 蓝 / 30-60% 橙 / <30% 红 |
| **EP** | 🟡 金 | >60% 金 / 30-60% 橙 / <30% 红 |
| **EXP** | 🟣 紫 | 仅数值（无血量条） |
| **TNL** | 灰 | 仅数值 |

---

## ANSI 颜色保留

### 工作流程

```text
原始字节: "type \x1b[32mlook\x1b[0m to view"
           ↓ [AnsiParser.splitByAnsi]
拆分:     ["type ", ANSI绿, "look", ANSI重置, " to view"]
           ↓ [逐段翻译 + ANSI 状态跟踪]
重建:     ["输入 ", ANSI绿, "look(看)", ANSI重置, " 来查看"]
           ↓ [AnsiRenderer.render]
输出:     AnnotatedString(绿="look(看)", 白="输入"/"来查看")
```

### 色彩方案

16 色 ANSI 调色板，与 BatMUD 原生客户端一致：

```text
标准色: 黑 红 绿 黄 蓝 品红 青 白
高亮色: 暗灰 亮红 亮绿 亮黄 亮蓝 亮品红 亮青 亮白
```

支持粗体、下划线、暗淡、闪烁、反转等文本样式。

---

## 缓存系统

两级缓存，兼顾速度与持久性：

```text
翻译请求
    │
    ▼
[L1] 内存 LRU（毫秒级）
    ├── 命中 → 返回
    └── 未命中
         │
         ▼
[L2] Room SQLite（毫秒级）
    ├── 命中 → 回填 L1 + 返回
    └── 未命中
         │
         ▼
百度翻译 API（数百毫秒）
    └── 结果 → 存入 L1 + L2
```

- L1 内存默认 2000 条，LRU 淘汰
- L2 Room 数据库持久化，重启手机后缓存仍在
- 再次遇到相同文本时直接本地命中，不消耗 API 额度

---

## 设置说明

| 设置项 | 说明 | 默认值 |
|--------|------|--------|
| 服务器地址 | BatMUD 服务器 | `batmud.bat.org` |
| 端口 | Telnet 端口 | `23` |
| APP ID | 百度翻译 API 凭据 | 必填 |
| Secret Key | 百度翻译 API 密钥 | 必填 |
| 翻译模型 | `llm`(大模型) / `nmt`(机器翻译) | `llm` |
| 缓存条数 | 内存 LRU 缓存容量 | `2000` |
| 最少翻译字符数 | 短于此值不翻译 | `4` |

### 模型选择

| 模型 | 特点 |
|------|------|
| `llm` | 大模型翻译，质量最好，理解语境，**推荐** |
| `nmt` | 神经机器翻译，速度快 |

### 百度 API 获取

1. 访问 [fanyi-api.baidu.com](https://fanyi-api.baidu.com/)
2. 注册百度账号 → 开通「大模型文本翻译API」（注意不是「通用翻译」）
3. 在控制台获取 APP ID 和 Secret Key
4. 填入 APK 设置页

---

## 构建

```bash
git clone https://github.com/stupidpig-heng/BatMUD_CN_Android.git
cd BatMUD_CN_Android

# 设置 Android SDK 和 JDK
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-21

# Debug APK
./gradlew assembleDebug

# Release APK（需配置签名）
./gradlew assembleRelease
```

| 环境要求 | 版本 |
|----------|------|
| Android SDK | 35 |
| JDK | 17+ |
| Kotlin | 2.0+ |
| Gradle | 8.9 |
| minSdk | 26 (Android 8.0) |

---

## 项目结构

```text
BatMUD_CN_Android/
├── README.md
├── build.gradle.kts              # 项目级 Gradle 配置
├── settings.gradle.kts
├── gradle.properties
│
└── app/
    ├── build.gradle.kts           # 应用级 Gradle（Compose + Room + OkHttp）
    │
    └── src/main/
        ├── AndroidManifest.xml    # 横屏锁定 + 网络权限
        │
        ├── java/com/batmudcn/
        │   ├── MainActivity.kt           # Compose 入口
        │   ├── BatMudApp.kt              # Application 类
        │   │
        │   ├── engine/                   # 核心引擎层
        │   │   ├── GameEngine.kt         # Telnet 连接 + 行处理调度
        │   │   ├── TelnetClient.kt       # Telnet 协议实现（IAC 协商 + MCCP 过滤）
        │   │   ├── TelnetConstants.kt    # Telnet 协议常量
        │   │   ├── LineProcessor.kt      # 字节流解析 + 硬折行合并 + ANSI 逐段翻译
        │   │   ├── AnsiParser.kt         # ANSI SGR 转义序列解析
        │   │   └── ByteBuffer.kt         # 可扩容字节缓冲区
        │   │
        │   ├── translate/                # 翻译层
        │   │   ├── Translator.kt         # 三级缓存翻译引擎
        │   │   ├── BaiduApiClient.kt     # 百度大模型翻译 API 客户端
        │   │   └── LruCache.kt           # LRU 内存缓存
        │   │
        │   ├── data/                     # 数据层
        │   │   ├── UserPreferences.kt    # DataStore 持久化设置
        │   │   ├── AppDatabase.kt        # Room 数据库
        │   │   ├── TranslationDao.kt     # 翻译缓存 DAO
        │   │   ├── TranslationEntity.kt  # 翻译缓存实体
        │   │   └── model/                # 数据模型
        │   │       ├── AnsiSegment.kt    # ANSI 段（text/code）
        │   │       ├── AnsiState.kt      # ANSI 颜色状态
        │   │       ├── ParsedLine.kt     # 解析后的行
        │   │       └── OutputLine.kt     # 输出行（text/html/raw/debug）
        │   │
        │   ├── viewmodel/
        │   │   └── GameViewModel.kt      # UI 状态管理
        │   │
        │   ├── ui/                       # UI 层（Jetpack Compose）
        │   │   ├── AnsiRenderer.kt       # ANSI → Compose AnnotatedString
        │   │   ├── screen/
        │   │   │   ├── GameScreen.kt     # 游戏主界面
        │   │   │   └── SettingsScreen.kt # 设置界面
        │   │   ├── component/
        │   │   │   ├── StatusBar.kt      # 连接状态栏
        │   │   │   ├── StatusHud.kt      # 角色状态 HUD（HP/SP/EP）
        │   │   │   ├── CommandInput.kt   # 命令输入框
        │   │   │   ├── TerminalOutput.kt # 终端输出（备用）
        │   │   │   └── DebugPanel.kt     # 调试面板
        │   │   └── theme/
        │   │       ├── Color.kt          # 终端暗色主题色板
        │   │       ├── Theme.kt          # Material3 主题
        │   │       └── Type.kt           # 字体样式
        │   │
        │   └── util/
        │       ├── Constants.kt          # 全局常量 + MUD 翻译指令
        │       └── Md5Utils.kt           # 百度 API MD5 签名
        │
        └── res/                          # 资源文件
            ├── values/
            │   ├── strings.xml
            │   └── themes.xml
            ├── drawable/
            └── mipmap-anydpi-v26/

```

### 核心模块说明

| 模块 | 职责 |
|------|------|
| `TelnetClient` | 管理与 BatMUD 服务器的 Telnet 连接，处理 IAC 协商、MCCP 压缩过滤 |
| `LineProcessor` | 核心翻译管线：字节流解析、硬折行合并、ANSI 逐段翻译、字节流重建 |
| `AnsiParser` | ANSI SGR 转义序列解析、拆分、状态跟踪、主色提取 |
| `AnsiRenderer` | ANSI 字节流 → Compose `AnnotatedString`（颜色、粗体、下划线等） |
| `Translator` | 三级缓存翻译引擎（内存 LRU → Room SQLite → 百度 API） |
| `BaiduApiClient` | 百度大模型翻译 API 客户端，MD5 鉴权 + QPS 限速 |
| `GameEngine` | 核心调度器，管理连接生命周期、数据流入、输出分发 |
| `GameViewModel` | UI 状态管理，暴露 StateFlow 供 Compose 订阅 |
| `StatusHud` | Prompt 行解析渲染，HP/SP/EP/Exp 固定显示 |

---

## 与 PC 版对比

| 特性 | PC 版 | Android 版 |
|------|-------|-----------|
| 技术栈 | Python + aiohttp + 浏览器 | Kotlin + Jetpack Compose |
| 翻译引擎 | 百度 LLM API | 百度 LLM API（同一套） |
| 翻译策略 | ANSI 逐段翻译 | ANSI 逐段翻译（一致） |
| 缓存 | LRU + SQLite | LRU + Room（一致） |
| 状态 HUD | Web 固定 div | Compose 固定组件 |
| ANSI 渲染 | CSS span 着色 | Compose AnnotatedString |
| 屏幕适配 | 浏览器自适应 | 横屏锁定（方案 C） |
| 字体 | 浏览器 Monospace | 系统 Monospace |
| 打包 | PyInstaller .exe | Gradle .apk |
| 网络栈 | asyncio + socket | OkHttp + raw Socket |

---

## 致谢

- BatMUD — 1990 年上线至今的传奇 MUD 游戏
- 百度翻译开放平台 — 大模型翻译 API
- Jetpack Compose — Android 原生声明式 UI 框架
