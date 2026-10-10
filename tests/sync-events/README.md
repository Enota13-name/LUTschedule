# 同步事件与会话互斥合成测试

在仓库根目录运行：

```powershell
$env:JAVA_HOME = 'D:\Android\jbr'
python tests/sync-events/run_tests.py
```

脚本编译仓库当前的生产 `SyncCoordinator.java` 与 `SiteGateway.java`，其余依赖用最小 Java 假对象替代，使用虚拟时钟，不等待实际网络或超时。结果输出到被 Git 忽略的 `tests/sync-events/build/results.json`。清理该目录前会验证解析后的绝对路径父目录为本测试目录且目录名是 `build`。

15 组断言覆盖原有 9 组场景及 6 组模块 HTTP 场景。原有场景包括：课表保存后才发 ready；后续考试读取失败不撤回已发 ready；快照保存失败无 ready；课表主文档 HTTP 403 保留状态码并终止剩余模块；关闭后的旧票据不回调；前台采集抢占后台 owner 并让旧任务以 `IDLE` 结束；后台采集不能抢占前台 owner；整轮等待受 120 秒总预算约束；401/403/429/503 状态映射。

新增模块场景在成绩模块注入主文档 HTTP 403、404、500、503，分别断言课表 ready 已发出、成绩模块失败、考试模块随后成功、最终状态仍为失败，且结束时间不超过 120 秒；另注入 401 与 429，断言分别以 `LOGIN_REQUIRED` 与 `UNREACHABLE` 终止，不继续考试模块。每个状态各算一组，总数为 9 + 4 + 2 = 15。

限制：Reader、Store、WebView、CookieManager、Handler 和状态机依赖均为测试替身。没有运行真实网页解析、SQLite、Android Cookie 持久化、JobScheduler 或主页绘制，也没有真实用户会话、官网 HTTP 请求和 UI 首帧计时。虚拟时钟只验证当前协调器的事件顺序、超时边界和 owner 规则，不能当作实网加速或 Android 生命周期验证。
