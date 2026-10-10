# 登录状态核对与自动登录方案

日期：2026-10-10。源码基线：v1.0.1 / 1.0.1 / versionCode 12。

本次为登录失效排查：审查当前源码、执行隔离合成测试，并沿用现有代理匿名读取官网认证方式选择页。未操作用户已登录的页面，未读取真实账号密码或浏览器 Cookie，未提交登录或教务业务表单，也未修改网络设置。生产代码、APK、版本号和 Release 均未改动。

## 结论与证据范围

**已确认 App 存在登录状态分类缺陷；尚不能确定用户手机上真正的会话失效原因。** 不能据此把全部提示归因于学校，也不能把源码中的 Cookie 保存调用当作已完成实机持久化验证。

| 核对项 | 结果 | 证据与限制 |
|---|---|---|
| 打开、返回或销毁 App 是否主动清除登录 | 未发现这种代码路径 | `MainActivity.onStop/onResume/onDestroy` 无清 Cookie 操作；显式清理位于用户主动选择的 `logout()` |
| 会话是否有保存调用 | 有 | 登录页面确认认证、手动课表读取成功，以及 `SiteGateway` 课表读取成功时均调用 `CookieManager.flush()` |
| 登录和采集是否使用独立 Cookie 容器 | 当前源码未设置独立容器 | 配置使用同一个 `CookieManager.getInstance()`；Manifest 无额外进程、源码无 WebView 数据目录后缀配置。不读取真实 Cookie 来验证 |
| 403 是否一定代表登录过期 | 否，但 App 当前一律这样分类 | 实际生产脚本的 fetch 与 jQuery 分支都将 403 标为 `login=true`；合成测试确认结果会传递给页面读取器 |
| 诊断能否保留原始认证错误码 | 不完整 | fetch 的 401/403 抛错未传入状态码，最终为 `http=0`；jQuery 分支保留对应码 |
| 官网实际会话有效期、同账号多端规则 | 未确认 | 匿名选择页和电脑浏览器成功不能证明手机会话的服务器有效期；未取得学校明确策略或实际失效时的同设备证据 |

`CookieManager.flush()` 的官方含义是把当前可访问 Cookie 写入持久存储；它不会延长服务器认可该会话的时间。销毁一个 WebView 与显式调用 `removeAllCookies()` 是不同操作，`LOAD_NO_CACHE` 也不能据此认定为删除登录 Cookie。参见 [Android CookieManager](https://developer.android.com/reference/android/webkit/CookieManager)。

HTTP 403 表示服务器理解请求但拒绝处理，拒绝原因可能与凭据无关。因此它不能单独证明会话过期。参见 [RFC 9110：403 Forbidden](https://www.rfc-editor.org/rfc/rfc9110.html#name-403-forbidden)。

## 已复现的分类问题

在 Node VM 中执行生产 `read-emap-course.js`，注入公开合成响应；随后执行生产 `read-course-page.js`，逐字段验证分类传递。共 **16 个场景通过**，没有实际 HTTP 请求。

| 注入条件 | fetch：login / http / networkError / complete | jQuery：login / http / networkError / complete |
|---|---|---|
| HTTP 401 | true / 0 / false / false | true / 401 / false / false |
| HTTP 403 | true / 0 / false / false | true / 403 / false / false |
| HTTP 429 | false / 429 / false / false | false / 429 / false / false |
| HTTP 503 | false / 503 / false / false | false / 503 / false / false |
| HTTP 200 登录 HTML | true / 0 / false / false | false / 200 / false / false，注入 parsererror |
| 网络失败 | false / 0 / true / false，TypeError | false / 0 / false / false，status 0 |
| 中止或超时 | false / 0 / true / false，AbortError | false / 0 / false / false，timeout |
| 正常完整合成数据 | false / 0 / false / true | false / 0 / false / true |

jQuery 的 HTML 用例模拟 `dataType:'json'` 解析失败，不证明官网实际上一定这样响应。网络和超时用例仅验证注入后的分类，没有测量真实网络或计时器行为。

生产链路中，`read-course-page.js` 将适配器的 `login/http` 原样带出，`SiteGateway` 优先将 `login=true` 判为 `LOGIN_REQUIRED`；`MainActivity` 再显示“登录已失效”并打开登录页。这个链路能解释**错误的重新登录提示**，不能证明它删除了 Cookie 或使服务器会话过期。

复跑入口：[tests/auth-classification](../tests/auth-classification/README.md)。这是现状复现测试，刻意断言当前行为；修复分类时必须同时更新对应预期，不能为保持测试通过而保留错误行为。

## 尚待验证的会话因素

- **真实手机持久化**：需要在 App 自己的 WebView 中，对正常关闭、进程被系统回收、重新启动分别验证同一已登录会话。电脑浏览器与 App 的会话各自独立。仅记录 Cookie 是否存在、会话确认结果、阶段和 HTTP 码，不记录 Cookie 值、账号或响应正文。
- **前后台竞争**：前台与 `IdleRefreshService` 各有协调器，共用当前应用的 WebView 会话，尚无全局互斥。并发或迟到结果风险已登记，但没有证据说明它造成了本次登录过期。
- **认证方式**：匿名读取的 [官网入口](https://jwxt.lut.edu.cn/jwapp/sys/yjsrzfwapp/dbLogin/main.do) 提供“本地登录”和“统一身份认证”。这只是方式选择页，没有据此确认实际密码页面的验证码、记住登录、二次认证或会话期限。匿名 Cookie 的期限不能当作登录会话期限。
- **第三方 Cookie**：当前目标 API 36，未设置第三方 Cookie 策略；Android 对目标 API 21+ 的 WebView 默认不接受第三方 Cookie。只有实际认证依赖跨站嵌入内容时，这才是待验证因素。顶层跳转、同一站点的子域名、官网有统一认证选项本身都不足以证明需要放开该设置。不得未经验证便全局启用第三方 Cookie。[官方默认策略](https://developer.android.com/reference/android/webkit/CookieManager#setAcceptThirdPartyCookies(android.webkit.WebView,%20boolean))

## 下一次修复的顺序

1. 保留原始 HTTP 码，分别处理认证需求、访问拒绝、限流、网络/超时和解析失败；403 不直接弹出“登录已失效”。返回登录页面、明确认证状态与已适配只读查询的结果应共同用于确认，不能把一次失败当作过期证据。
2. 统一前后台同步互斥、取消与过期回调规则，复测 Cookie 持久化及登录后的自动课表采集。失败保留课表和会话，不通过清 Cookie 尝试修复连接问题。
3. 记录不含秘密的失败阶段、实际 HTTP/WebView 码与会话确认结果，再判断是否是学校的服务端失效。需要同设备、同时间的证据，不推测固定有效期。

验收应补充 App 原生集成测试：进程重建、退出清理、前后台竞争、401/403/429/5xx、登录 HTML 和网络失败。当前 16 个场景没有运行 Android Cookie 存储、服务器认证、原生生命周期或自动登录，不代表这些能力已验证。

## 保存密码能否自动登录

**可以设计成用户明确选择开启的自动重新登录，但现在没有实现。** 保存密码只减少重新输入；它不会阻止服务器让会话过期，也不能保证每次登录成功。

建议先修复会话识别和验证持久化，再评估自动登录。优先使用可用的系统密码填充；如果应用自己保存凭据，则用 Android Keystore 中的密钥加密，密文仅保存在应用私有存储，不放进普通明文偏好文件、剪贴板、日志、错误报告或 GitHub。保留当前不备份应用数据的策略，并提供关闭功能与立即删除凭据的入口。Keystore 保护密钥，但不等于密码在所有使用场景都不可被读取。[Android Keystore](https://developer.android.com/privacy-and-security/keystore)

拟议流程：先复用并确认会话；明确需要认证且用户已开启功能时，在已核对的官网 HTTPS 登录流程中最多尝试一次；成功后确认会话并继续课表采集。若遇验证码、二次认证、密码错误或新增认证步骤，转为全屏官网页面让用户完成，停止自动重试；网络不可达或 403 不触发反复提交密码。初期仅前台重连，是否允许后台使用凭据另行由用户选择。该方案不绕过认证，也不自动提交任何教务业务。

源码入口：[SiteGateway](../app/src/main/java/cn/lut/schedule/SiteGateway.java)、[MainActivity](../app/src/main/java/cn/lut/schedule/MainActivity.java)、[SyncCoordinator](../app/src/main/java/cn/lut/schedule/SyncCoordinator.java)、[IdleRefreshService](../app/src/main/java/cn/lut/schedule/IdleRefreshService.java)、[课表接口适配器](../app/src/main/assets/read-emap-course.js)、[页面读取器](../app/src/main/assets/read-course-page.js)。
