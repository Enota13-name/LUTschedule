# 登录状态分类合成测试

此测试在 Node.js VM 中直接执行生产资源 `app/src/main/assets/read-emap-course.js`，只注入假 DOM、合成 JSON/HTML、假 `fetch` 或假 jQuery AJAX。不会访问网络、真实教务账号、Cookie 或凭据，也不修改生产代码。

运行命令（在仓库根目录）：

```powershell
node tests/auth-classification/run.js
```

断言覆盖 fetch 与 jQuery 两种分支的 HTTP 401/403/429/503、HTTP 200 登录 HTML、网络失败、fetch AbortError、jQuery timeout 及正常合成数据，并在同一有限时长 VM 中继续执行 `read-course-page.js`，检查 `login/http/networkError/complete` 是否传递到页面读取结果。HTML 登录页在 jQuery 情况下被模拟为 `dataType:'json'` 的 parsererror；这只是模拟的传输行为，不断言真实站点一定以此方式返回登录页。

已知分类差异：fetch 的 401/403 都为 `login=true, http=0`；jQuery 的 401/403 为 `login=true, http=对应状态码`。这表示目前任何接口 403 都可能被误认作会话失效，即使拒绝来自接口权限或临时策略。HTTP 200 登录 HTML 在 fetch 分支按正文标记为登录；jQuery 的 parsererror 模拟结果为 `login=false`。fetch 的 TypeError/AbortError 被标为网络错误；jQuery 的状态码 0（包括本测试注入的网络失败和 timeout）当前未设 `networkError`。

共 16 个场景，每例逐字段断言适配器及页面读取结果。VM 脚本执行设有有限时长，异步结果须在有限微任务轮次内结束；不运行真实网络、超时计时器、Android Cookie 存储或原生生命周期。

测试只描述静态适配器逻辑的合成响应，不证明真实服务器返回类型、用户会话状态或线上故障原因，也不代表问题已修复。这是现状复现测试，刻意断言现有缺陷；修复时必须更新对应预期，不得为了通过它而保留错误分类。排查记录见 [登录状态核对](../../docs/SESSION_AUDIT_2026-10-10.md)。
