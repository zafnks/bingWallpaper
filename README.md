# Bing 壁纸自动更换

Windows 桌面壁纸自动更换工具，每天从 Bing 获取每日一图，设为桌面背景，支持历史浏览、定时切换、自动清理等。

## 功能

- **自动获取** — 启动时自动拉取 Bing 每日壁纸（支持 1080P / 1920×1200 / 4K 分辨率）
- **文字叠加** — 版权描述以毛玻璃效果渲染在壁纸右下角
- **历史浏览** — 缩略图网格窗口，可回看和切换历史壁纸
- **定时切换** — 支持每天固定时间或每 N 小时自动更新
- **导航切换** — 托盘菜单上一张/下一张翻阅历史
- **自动清理** — 按保留天数自动删除旧壁纸
- **开机自启** — 一键注册/取消 Windows 启动项

## 系统要求

- Windows 10 / 11
- Java 8+

## 快速开始

```bash
# 构建
build.bat                  # 自动下载 Maven 并构建

# 运行
java -jar bing-wallpaper.jar
```

## 构建说明

```bash
# 使用 Maven Wrapper
./mvnw clean package       # Unix / WSL
mvnw.cmd clean package     # CMD

# 使用系统 Maven
mvn clean package
```

构建产物直接输出到根目录 `bing-wallpaper.jar`。

## 项目结构

```
src/main/java/com/bingwallpaper/
├── App.java                # 入口，组装各模块
├── WallpaperService.java   # 核心编排：拉取、切换、调度、清理
├── BingFetcher.java        # 调用 Bing API，下载图片，叠加文字
├── WallpaperChanger.java   # 通过 JNA 设置 Windows 壁纸
├── WallpaperHistory.java   # 壁纸历史记录（JSON 持久化），支持修复
├── SchedulerService.java   # 定时任务调度
├── Settings.java           # 配置持久化
├── TrayManager.java        # 系统托盘图标与右键菜单
├── TrayCallback.java       # 托盘 UI 回调接口
└── HistoryWindow.java      # 壁纸历史浏览窗口（缩略图网格）
```

## 数据存储

所有数据保存在 `~/.bing-wallpaper/`：

- `settings.json` — 应用配置
- `history.json` — 壁纸历史（有序路径列表）
- `wallpapers/` — 下载的壁纸，命名 `bing_wallpaper_YYYY-MM-DD.jpg`

## 技术栈

- Java Swing（托盘图标、历史窗口）
- JNA（调用 Windows User32 API 设置壁纸）
- Gson（JSON 序列化）
- Maven Shade Plugin（构建 fat JAR）
