# Android 正式版合成测试

当前版本 1.0.2。RuntimeSmoke 的 design 136、protocol 38、regression 42 与 update 89 项断言通过，共 305 项；另外执行合成 Cookie 写入、强停进程与重建读取，以及生产入口冷启动和后续进程/崩溃日志检查。测试使用合成数据，不包含学校账号，不调用演示模式，也不把控制端失败或重跑累计进通过数。

仅在专用模拟器使用。测试会清空该 App 的测试缓存、背景和设置，授予通知权限，写入合成成绩并强制一次符合条件的后台任务。不要对日常 App 数据运行。

```powershell
.\build-offline.ps1 -JdkPath $env:JAVA_HOME
.\tests\android\build-runtime-test.ps1 -JdkPath $env:JAVA_HOME
adb -s emulator-5580 install -r ..\LUT-Schedule-1.0.2.apk
adb -s emulator-5580 install -r .\tests\android\runtime-test-build\runtime-test.apk
adb -s emulator-5580 shell am instrument -w -r -e phase design cn.lut.schedule.tests/.RuntimeSmoke
adb -s emulator-5580 shell am instrument -w -r -e phase protocol cn.lut.schedule.tests/.RuntimeSmoke
adb -s emulator-5580 shell am instrument -w -r -e phase regression cn.lut.schedule.tests/.RuntimeSmoke
adb -s emulator-5580 shell am instrument -w -r -e phase update cn.lut.schedule.tests/.RuntimeSmoke
adb -s emulator-5580 shell am instrument -w -r -e phase session-write cn.lut.schedule.tests/.RuntimeSmoke
adb -s emulator-5580 shell am force-stop cn.lut.schedule
adb -s emulator-5580 shell am instrument -w -r -e phase session-read cn.lut.schedule.tests/.RuntimeSmoke
```

App 与测试 APK 必须同签名。使用非默认构建目录时为测试传入 -SigningKey；签名私钥不公开。逐阶段执行，每阶段需 PASS 和 INSTRUMENTATION_CODE: -1，超时不能算通过。session-write 与 session-read 之间保留 App 数据并强停进程，不清 Cookie。

| 阶段 | 断言数 | 覆盖重点 |
|---|---:|---|
| RuntimeSourceUi / design | 136 | 首次来源、无虚构课程、导入隔离、23 项禁用、六种底栏顺序及真实对话框选择、三页整体主色字体、日期无整条底板、暗色彩色课程、房间号/色盘、双指裁剪、固定署名和五击剪贴板 |
| RuntimeOfficialProtocol / protocol | 38 | 23 条直达路由、成绩完整分页、考试发布和时间、课表/校历、全屏官网、登录后自动采集路线 |
| RuntimeRegression / regression | 42 | 成绩基线与账户隔离、未读/通知去重/顶部提示、考试日期区域、缓存、一次性持久后台任务、样式重建与自定义排列持久化 |
| RuntimeStorage / update | 9 | 收件箱100条/90天、静默日志24条、不置待弹框标记、旧通知去重迁移和有界清理 |
| RuntimeCredentials / update | 40 | Keystore与AES-GCM、替换、密文无明文、篡改/超大文件、删除与精确官方来源限制 |
| RuntimeUpdateUi / update | 40 | 课表就绪立即对号、后续失败保留对号、考试独立更新、旧请求/来源/生命周期防护、可选提示/取消/安全编辑/删除按钮 |
| RuntimeSession | 不计入305 | 虚构域名的持久和会话Cookie在强停进程后观察；不读取真实Cookie，不验证服务器会话期限 |

原生模拟器为 Android 15 / API 35，WebView 124.0.6367.219。额外 [响应分类](../auth-classification/README.md) 17 场景、登录脚本5场景和 [协调器事件](../sync-events/README.md) 15 组通过；虚拟时钟不算真实网络秒数。部分源码反射测试适配了新的 Events/会话所有权接口。生命周期旧回调测试直接驱动 Activity 钩子，不能替代实际系统后台压力测试。

生产入口另做冷启动和网络等待检查：

```powershell
adb -s emulator-5580 shell am force-stop cn.lut.schedule
adb -s emulator-5580 shell am start -W -n cn.lut.schedule/.BootstrapActivity
adb -s emulator-5580 logcat -d AndroidRuntime:E '*:S'
```

实际结果见 [runtime-result.txt](runtime-result.txt)、[launcher-result.txt](launcher-result.txt) 和 [../../verification.json](../../verification.json)。学校协议已用浏览器只读核对，合成响应成功仍不证明真实账号同步或所有业务操作成功。恢复机制和错误报告压力测试在相关改动时补充，不把未执行专项列为本次通过。
