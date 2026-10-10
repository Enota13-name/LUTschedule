# 登录状态分类合成测试

此测试在 Node.js VM 中直接执行生产资源 `app/src/main/assets/read-emap-course.js`，只注入假 DOM、合成 JSON/HTML、假 `fetch` 或假 jQuery AJAX。不会访问网络、真实教务账号、Cookie 或凭据，也不修改生产代码。

运行命令（在仓库根目录）：

```powershell
node tests/auth-classification/run.js
node tests/auth-classification/login-form.js
```

断言覆盖 fetch 与 jQuery 两种分支的 HTTP 401/403/429/503、HTTP 200 登录 HTML、jQuery 非登录 parsererror、网络失败、fetch AbortError、jQuery timeout 及正常合成数据，并在同一有限时长 VM 中继续执行 `read-course-page.js`，检查 `login/http/networkError/complete` 是否传递到页面读取结果。HTML 登录页在 jQuery 情况下被模拟为 `dataType:'json'` 的 parsererror；这只是模拟的传输行为，不断言真实站点一定以此方式返回登录页。parsererror 的原始响应只供内存中的登录页特征检查，测试断言原始正文标记不会进入错误文本。

预期分类：401 可识别为认证失效并保留原状态码；403 只保留拒绝状态，不单独触发重新登录；429/503 保留原状态码。登录 HTML 在两种传输分支均识别为认证页。fetch 的 TypeError/AbortError 与 jQuery 状态码 0 的网络失败/timeout 均标为网络错误。jQuery parsererror 通过 `xhr.responseText` 检查登录页特征，但原始正文不会写入错误结果。

分类测试共 17 个场景，每例逐字段断言适配器及页面读取结果。登录表单脚本另有 5 个纯 JS 场景，检查官方页、外站、子框、验证控件和手工输入；包含可执行字符串的合成账号验证 JSON 参数不会变成代码，脚本不使用 bridge/global credential 属性，也不输出秘密。VM 脚本执行设有有限时长，异步结果须在有限微任务轮次内结束；不运行真实网络、超时计时器、Android Keystore、Cookie 存储或原生生命周期。

测试只描述静态适配器逻辑的合成响应，不证明真实服务器返回类型、用户会话状态或线上故障原因。排查记录见 [登录状态核对](../../docs/SESSION_AUDIT_2026-10-10.md)。
