# 课间 · LUT 1.0.0

轻量 Android 课表应用。登录兰州理工大学教务系统，或导入本地 JSON 课表；每次启动直接进入自己绘制的课表。

**[下载正式版 APK](releases/LUT-Schedule-1.0.0.apk)** · Android 8.0 及以上 · 约 113 KB · 开发者 **Enota13** · 非官方、不可商用

<p><img src="docs/screenshots/home.png" width="230" alt="原生课表"><img src="docs/screenshots/academic.png" width="230" alt="教务目录"><img src="docs/screenshots/settings.png" width="230" alt="分组设置"></p>

- 默认底栏为 **教务 — 课表 — 设置**，设置里可选择六种顺序；首页始终是课表。
- 三个页面共用一种字体颜色。全屏背景支持双指裁剪、共用图片或连续三份，切换时有轻量动画。
- 课表保留实际小节，今天独立标记、日期间无底板和方格，房间号独占课程最下行；考试显示具体日期和时间。
- 官网自己登录、全屏 WebView、23 项教务直达入口；导入模式禁用官网服务与后台同步。
- 官网模式打开时更新，失败保留缓存；新成绩提供顶部和 Android 提醒，闲置后的单次早八检查只更新缓存。

[重点功能](docs/FEATURES.md) · [最终需求提示词](docs/FINAL_PROMPT.md) · [Vibe coding 复盘](docs/VIBE_CODING_REVIEW.md) · [课表导入格式](docs/IMPORT_FORMAT.md) · [构建与验证](docs/DEVELOPMENT.md) · [错误报告排查](docs/ERROR_REPORTS.md)

当前页面与下载区只展示正式版。验证使用自有 Android 模拟器和合成数据；小米 15 Pro 实机及 App 内真实学校账号全量同步尚未完成验证。官网需要有效网络和登录，后台实际执行可能受 Android 节电限制。详细证据与边界见 [verification.json](verification.json)。
