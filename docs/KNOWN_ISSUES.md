# 已知问题与待修复项

记录日期：2026-10-10。适用版本：1.0.1 / versionCode 12。以下均为待修复项，登记不代表修复已交付。

依据：用户反馈和与 v1.0.1 一致的本地源码审查。这里只保存脱敏现象、代码依据与验收要求，不保存用户原始报告、设备标识、事件编号、账号、课程或成绩内容。尚未在真实手机上复现或测量容量。

## SYNC-001：重新进入应用时频繁弹出教务同步错误

状态：待修复。优先级：高。

现象：离开后重新进入应用，出现普通连接超时的错误提示；旧失败提示可能与新一轮更新状态同时存在。所反馈事件为非致命运行失败，没有本地崩溃堆栈。

已确认的机制：

- `MainActivity.refresh()` 把同步失败送入 `Diagnostics.event()`，同时记录并请求弹框。`ErrorReports.record()` 保存待提示标志，`Diagnostics.attach()` 在返回前台时再次检查，所以普通网络失败也可能显示通用错误弹窗。
- `MainActivity.onStop()` 没有取消前台采集，`onDestroy()` 才清理。返回会请求刷新，但同一 Activity 正处于 `UPDATING` 时有保护，不会因此并行启动另一轮。
- `SyncCoordinator` 顺序读取课表、成绩、考试与五项辅助服务，页面各有 35 秒超时。失败后仍可能继续下一模块，整轮耗时会累积。
- 前台与 `IdleRefreshService` 使用各自的协调器，没有统一运行互斥。存在并发风险，但报告未证明此次发生了并发。
- “后台更新连接”是 `SiteGateway` 的统一阶段名，不能据此断言闲置早八任务执行了多次。`code=0` 可能是未取得错误码时的默认值；部分模块只记录“无法连接官网”，缺少底层原因。

仍待确认：失败发生在页面、认证还是接口阶段；官网或网络路径是否异常；设备后台调度对回调的影响。网络能力快照与 WebView 版本不能单独证明这些原因。

修复与验收要求：

1. 普通网络失败保留有效缓存，用状态按钮提示并静默记日志；本地异常继续提供诊断弹框。后台检查保持静默。
2. 统一前后台采集的生命周期、取消与互斥规则，避免返回时把旧连接失败呈现为新的本地异常。
3. 优先完成课表、成绩和考试，辅助查询后置；明确整轮预算，避免无效串行等待。
4. 记录脱敏的阶段、实际 WebView/HTTP 错误码、取消原因及耗时，不记录页面内容或认证信息。
5. 用合成响应验证超时后返回、取消、前后台竞争、登录失效与部分成功；缓存完整、状态正确、通知不重复。

代码入口：[MainActivity](../app/src/main/java/cn/lut/schedule/MainActivity.java)、[SyncCoordinator](../app/src/main/java/cn/lut/schedule/SyncCoordinator.java)、[SiteGateway](../app/src/main/java/cn/lut/schedule/SiteGateway.java)、[Diagnostics](../app/src/main/java/cn/lut/schedule/Diagnostics.java)、[ErrorReports](../app/src/main/java/cn/lut/schedule/ErrorReports.java)、[IdleRefreshService](../app/src/main/java/cn/lut/schedule/IdleRefreshService.java)。

## STORAGE-001：变更收件箱与通知去重记录缺少总量限制

状态：待修复。优先级：中。尚无实际占用测量，不能据此断言已大量占用手机。

| 内容 | 保存策略 | 是否每次同步追加整份数据 |
|---|---|---|
| 课表 | `snapshots.source` 主键与 `CONFLICT_REPLACE`，官网、导入各一份 | 否，校验成功后替换；失败保留旧缓存 |
| 成绩、考试、辅助教务数据 | `modules.name` 主键与 `CONFLICT_REPLACE`，每模块一份当前快照 | 否，完整结果替换旧快照 |
| 未确认变更 | `changes.id` 唯一键与 `CONFLICT_IGNORE`，新增真实成绩或考试变化 | 相同事件不重复增加，不同修订可累积；没有条数或年龄上限 |
| 通知去重 | `notification_seen` 为已通知成绩事件保存标志 | 相同 ID 不增加，新 ID 会累积；没有淘汰策略 |
| 错误与操作日志 | 同一偏好键保存环形记录，最多 12 条错误、24 条操作 | 不无限追加，也不自动保存每次完整报告为文件 |
| 自定义背景 | 临时写入并原子替换 `background.jpg` | 否，只保留当前背景 |

`AcademicStore.acknowledge()` 删除用户确认的变更，账户范围变化时清理变更；清除数据并退出时清理变更和通知去重标志。但确认变更后，不会同时清理通知去重标志。

网页 Cookie 与 DOM Storage 由 WebView 管理，目前未设置应用级总容量上限。`LOAD_NO_CACHE` 不是 DOM Storage 的容量限制；清除数据并退出时有显式网页存储清理。容量治理须保留正常登录所需状态。

手机存储与运行内存需要分别评估：快照替换避免整份历史堆积；未读变更仍会持久累积，且 `pending()` 一次加载全部未读条目，也会增加运行内存。图片解码、裁剪和 WebView 有独立内存开销。错误报告中的 Java 堆用量不是整个应用及网页进程的总内存。

修复与验收要求：

1. 为变更与通知去重记录制定条数、期限及淘汰策略，保留最近有效提醒，避免淘汰后对仍未读事件重复通知。
2. 合成验证重复刷新不增加快照行数、相同变更不重复入库、长期不同修订仍受限；确认、换账户、退出和部分失败不破坏缓存。
3. 分别测量数据库、偏好文件、网页存储、背景的磁盘占用和采集/裁剪的运行内存峰值；未测量前不承诺固定总容量。

代码入口：[SnapshotStore](../app/src/main/java/cn/lut/schedule/SnapshotStore.java)、[AcademicStore](../app/src/main/java/cn/lut/schedule/AcademicStore.java)、[GradeNotifications](../app/src/main/java/cn/lut/schedule/GradeNotifications.java)、[BackgroundCrop](../app/src/main/java/cn/lut/schedule/BackgroundCrop.java)。
