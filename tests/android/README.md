# Android 正式版合成测试

当前入口 RuntimeSmoke，按来源与界面、官网协议、回归三阶段执行。测试使用合成导入文件和合成官网响应，不包含学校账号，也不调用应用内演示模式。

仅在专用模拟器使用。测试会清空该 App 的测试缓存、背景和设置，授予通知权限，写入合成成绩并强制一次符合条件的后台任务。不要对日常 App 数据运行。

```powershell
.\build-offline.ps1 -JdkPath $env:JAVA_HOME
.\tests\android\build-runtime-test.ps1 -JdkPath $env:JAVA_HOME
adb -s emulator-5580 install -r ..\LUT-Schedule-1.0.0.apk
adb -s emulator-5580 install -r .\tests\android\runtime-test-build\runtime-test.apk
adb -s emulator-5580 shell am instrument -w -r -e phase design cn.lut.schedule.tests/.RuntimeSmoke
adb -s emulator-5580 shell am instrument -w -r -e phase protocol cn.lut.schedule.tests/.RuntimeSmoke
adb -s emulator-5580 shell am instrument -w -r -e phase regression cn.lut.schedule.tests/.RuntimeSmoke
```

App 与测试 APK 必须同签名。使用非默认构建目录时为测试传入 -SigningKey；签名私钥不公开。控制端有时间上限时分三次执行；每阶段需 PASS 和 INSTRUMENTATION_CODE: -1，超时不能算通过。

| 阶段 | 实际断言 | 覆盖重点 |
|---|---:|---|
| RuntimeSourceUi / design | 136 | 首次来源、无虚构课程、导入隔离、23 项禁用、六种底栏顺序及真实对话框选择、三页整体主色字体、日期无整条底板、暗色彩色课程、房间号/色盘、双指裁剪、固定署名和五击剪贴板 |
| RuntimeOfficialProtocol / protocol | 38 | 23 条直达路由、成绩完整分页、考试发布和时间、课表/校历、全屏官网、登录后自动采集路线 |
| RuntimeRegression / regression | 42 | 成绩基线与账户隔离、未读/通知去重/顶部提示、考试日期区域、缓存、一次性持久后台任务、样式重建与自定义排列持久化 |

生产入口另做冷启动和网络等待检查：

```powershell
adb -s emulator-5580 shell am force-stop cn.lut.schedule
adb -s emulator-5580 shell am start -W -n cn.lut.schedule/.BootstrapActivity
adb -s emulator-5580 logcat -d AndroidRuntime:E '*:S'
```

实际结果见 [runtime-result.txt](runtime-result.txt)、[launcher-result.txt](launcher-result.txt) 和 [../../verification.json](../../verification.json)。学校协议已用浏览器只读核对，合成响应成功仍不证明真实账号同步或所有业务操作成功。恢复机制和错误报告压力测试在相关改动时补充，不把未执行专项列为本次通过。
