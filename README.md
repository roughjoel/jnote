<p align="center">
  <img src="src/main/resources/com/jnote/images/jnote-app-icon-128.png" width="112" height="112" alt="JNote Logo">
</p>

<h1 align="center">JNote</h1>

<p align="center">
  一款轻量、专注的 JavaFX 桌面笔记应用。
</p>

<p align="center">
  <img alt="Java 17" src="https://img.shields.io/badge/Java-17-718A99">
  <img alt="JavaFX 17" src="https://img.shields.io/badge/JavaFX-17-AEBBAF">
  <img alt="Maven" src="https://img.shields.io/badge/build-Maven-CB8B6B">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Windows-718A99">
</p>

JNote 面向 Markdown 写作、开发笔记和本地知识整理。它将文件操作、编辑、阅读与代码高亮集中在一个桌面工作区中，笔记始终保存在用户自己的文件系统里。

## 主要功能

- 编辑和保存 `.md`、`.markdown`、`.txt`、`.docx` 文件。
- 读取旧版 `.doc` 文件；保存时建议迁移到 `.docx`。
- 类似 Obsidian Live Preview 的 Markdown 源码保留编辑体验。
- 支持标题、列表、代码块、图片、网页链接及常见语言语法高亮。
- 一级、二级标题可折叠展开，并按文件恢复上次查看状态。
- 可直接粘贴剪贴板图片，图片自动保存到笔记同级资源目录。
- 多页签工作区，支持恢复上次打开的文件和最近目录。
- 右侧文件树可同时管理多个顶级目录，并支持重命名、移除和删除。
- Markdown 文件重命名时同步关联图片目录及文内图片引用。
- 多尺寸窗口、任务栏和 Windows EXE 图标。

## 界面结构

- 顶部工具栏：新建、保存、查找、撤销、重做、插入图片等常用操作。
- 文件页签栏：管理已打开文件并快速切换。
- Markdown 工作区：在单一视图中完成阅读与编辑。
- 右侧文件树：管理本地目录和文件。
- 底部状态栏：展示当前文档状态。

高保真 UI 原型见 [`design/jnote-ui-prototype.html`](design/jnote-ui-prototype.html)，设计说明见 [`design/UI_DESIGN.md`](design/UI_DESIGN.md)。

## 环境要求

- JDK 17 或更高版本
- Maven 3.9 或更高版本
- Windows 10/11（当前打包脚本的目标平台）

## 快速开始

克隆仓库并启动开发版本：

```powershell
git clone https://github.com/roughjoel/jnote.git
cd jnote
mvn javafx:run
```

运行测试：

```powershell
mvn test
```

构建 JAR：

```powershell
mvn package
```

## 构建 Windows 便携版

项目使用 `jpackage` 生成自带 Java 运行时的便携应用。执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1
```

构建完成后，可执行文件位于：

```text
target\package\Jnote\Jnote.exe
```

分发时请复制整个 `target\package\Jnote` 目录，而不是只复制 EXE。开发阶段如需跳过测试，可添加 `-SkipTests`：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1 -SkipTests
```

## 快捷键

| 快捷键 | 功能 |
| --- | --- |
| `Ctrl+B` | 收起或展开右侧文件树 |
| `Ctrl+F` | 聚焦查找 |
| `Ctrl+Z` / `Ctrl+Y` | 撤销 / 重做 |
| `Ctrl+N` | 新建文件 |
| `Ctrl+Shift+N` | 新建文件夹 |
| `Ctrl+S` | 保存当前文件 |
| `Ctrl+V` | 将剪贴板图片粘贴到 Markdown 文件 |
| `F2` | 重命名文件树中的选中项 |
| `Backspace` / `Delete` | 删除空行或在块边界恢复 Markdown 源码标记 |

## 项目结构

```text
src/main/java/com/jnote/
├── JnoteApplication.java       # JavaFX 主界面与交互
├── JnoteLauncher.java          # 应用入口
├── io/                         # 原子写入、文件读写和路径策略
├── markdown/                   # Markdown 渲染、编辑与代码高亮
├── model/                      # 文档模型和格式定义
└── state/                      # 最近文件与目录状态

src/main/resources/com/jnote/
├── images/                     # SVG Logo 和多尺寸应用图标
└── styles/app.css              # JavaFX 界面样式

src/test/java/com/jnote/        # JUnit 测试
src/main/packaging/jnote.ico    # Windows 可执行程序图标
scripts/package-windows.ps1     # Windows 便携版打包
scripts/generate-logo-assets.py # Logo 与图标资源生成
design/                         # UI 原型与设计说明
```

## 本地数据与隐私

应用状态保存在：

```text
%USERPROFILE%\.jnote\state.properties
```

仓库的 `.gitignore` 默认排除 `notes/`、`.jnote/`、构建产物和 IDE 配置，避免误提交个人笔记、最近文件路径或本地运行状态。

## 开发说明

- 测试框架：JUnit 5
- Word 文档支持：Apache POI
- 桌面 UI：JavaFX Controls / WebView
- Logo 主文件：`src/main/resources/com/jnote/images/jnote-logo-mark.svg`
- 当前版本仍在持续开发，欢迎通过 Issue 反馈问题或提出建议。
