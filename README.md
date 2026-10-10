<div align="center">

<img src="docs/assets/cover.svg" width="100%" alt="牛逼课表 · 一眼看清一周">

<p>为兰州理工大学校园生活设计的轻量 Android 课表应用。</p>

<p>
<a href="https://github.com/Enota13-name/LUTschedule/releases/latest"><strong>下载 APK</strong></a>
&nbsp; · &nbsp;
<a href="docs/FEATURES.md">探索功能</a>
&nbsp; · &nbsp;
<a href="https://github.com/Enota13-name/LUTschedule/releases/tag/v1.0.2">版本详情</a>
</p>

<p><strong>1.0.2</strong> &nbsp; / &nbsp; Android 8.0+ &nbsp; / &nbsp; 269 KiB &nbsp; / &nbsp; 原生 Java</p>

</div>

## 一周的安排，清楚地放在眼前

打开即是课表。课程、教室与考试时间各有位置，今天的安排一眼可见。教务入口、自定义背景和常用操作围绕这张课表展开，让每天反复查看的几件事更顺手。

<table>
<tr>
<td align="center"><img src="docs/screenshots/home.png" width="240" alt="原生周课表与独立的今天标记"></td>
<td align="center"><img src="docs/screenshots/academic.png" width="240" alt="按官网分类排列的教务直达目录"></td>
<td align="center"><img src="docs/screenshots/settings.png" width="240" alt="按功能分组的个性化设置"></td>
</tr>
<tr>
<td align="center"><strong>课表</strong><br>课程与考试，落在对应日期</td>
<td align="center"><strong>教务</strong><br>按类别直达官方服务</td>
<td align="center"><strong>设置</strong><br>把常用操作调整成你的习惯</td>
</tr>
</table>

<sub>以上为实际 Android 界面截图，使用合成数据，不含真实学生记录。</sub>

## 细节，为每天的使用服务

| 体验 | 设计 |
|---|---|
| **清楚的时间与地点** | 保留实际小节、周次与单双周；今天独立标记，日期之间没有连成一条的底板。课程房间号固定在最底行，考试显示具体日期与起止时间。 |
| **连贯的个人风格** | 自定义背景覆盖三个页面，支持双指缩放与裁剪、共用一张或连续三份。字体跨页保持同色，页面切换带有短动画。亮暗风格、多色课程与纯色色盘均可选择。 |
| **顺手的页面与操作** | 默认「教务 — 课表 — 设置」，支持六种底栏排列。每次启动始终进入课表；周数操作可放在左下角或右下角。 |
| **课表优先，模块继续** | 课表校验并保存后立即显示对号，其他查询继续运行。对号表示课表已更新，不代表所有模块完成；考试等模块独立更新并记录时间。 |
| **失败时保留有效数据** | 普通官网断网、超时或访问拒绝时静默记录原因并保留缓存。新成绩按既有规则提示；闲置后的单次早八检查仅更新缓存。 |
| **教务服务直达** | 23 项教务服务按官网类别排列，在全屏 WebView 中打开对应页面；「我的课表」返回原生主页。 |

## 从你的课表开始

首次打开时，可以选择两种数据来源：

- **登录 LUT 教务系统**：在官方网页自行登录，确认会话后进入个人课表采集流程。
- **导入本地课表**：使用[标准 JSON 文件](docs/IMPORT_FORMAT.md)。导入模式独立保存数据，教务入口不可用，也不会执行官网后台同步。

课表由已验证的官网数据或导入文件构建。官网登录与所有教务业务页面均以全屏呈现。

## 轻量，也对数据保持克制

原生 Java 实现，无第三方运行时、广告或统计 SDK。教务数据与背景保存在本机，错误报告由用户自行复制分享。自动采集限于已适配的只读查询，不自动办理评教、选课、申请或收费。

可选的本机凭据保存使用 Android Keystore 与 AES-GCM 加密。首次使用或升级时会提示，用户可以拒绝，也可在设置中更改或删除。App 仍需联网；本机加密降低保存风险，但不能保证绝无泄露，也不会延长学校会话期限。

## 正式版与验证

当前正式版为 **1.0.2 / versionCode 13**，源码标识为 `v1.0.2`。首页和下载区链接到 GitHub 最新正式版。

当前版本的构建和测试仍在进行，具体已完成项目、结果与限制以 [verification.json](verification.json) 及其中列出的测试记录为准；本页不预先汇总未完成测试，也不沿用旧版本的测试结论。真实学校账号自动登录、App 内原生全量同步，以及 Xiaomi 15 Pro / API 36 的运行表现尚未验证。真实网络间歇失败的根因也未定位。

## 项目文档

| 阅读方向 | 文档 |
|---|---|
| 更新与问题范围 | [本版重点更新](docs/UPDATE_1.0.2.md) · [问题记录](docs/KNOWN_ISSUES.md) · [课表优先与网络核对](docs/SYNC_PERFORMANCE_2026-10-10.md) · [登录状态核对](docs/SESSION_AUDIT_2026-10-10.md) |
| 使用与体验 | [重点功能与截图](docs/FEATURES.md) · [课表导入格式](docs/IMPORT_FORMAT.md) |
| 需求与设计过程 | [最终需求提示词](docs/FINAL_PROMPT.md) · [Vibe coding 复盘](docs/VIBE_CODING_REVIEW.md) |
| 开发与维护 | [构建与验证](docs/DEVELOPMENT.md) · [错误报告排查](docs/ERROR_REPORTS.md) · [图标素材说明](docs/ICON.md) |

<details>
<summary>一条非常直接的设计评审</summary>

> 你把github的介绍写的高大上一点好不好，现在显得好廉价

——来自需求方。于是，这个 README 也终于有了自己的设计任务。

</details>

---

<div align="center">
<strong>Enota13</strong><br>
app图标作者：他不想署名<br>
牛逼课表 · 非官方应用 · 不可商用<br>
<a href="NOTICE.txt">使用声明</a>
</div>
