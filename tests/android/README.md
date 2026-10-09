# Android 合成集成测试

当前入口 `suite=v019` 依次运行 RuntimeV019、RuntimeV017、RuntimeV016。验证首次数据来源选择、无虚构课程、旧配置迁移、导入隔离与全部 23 项教务服务禁用、无官网后台任务、统一主色字体、无日期整条底板、暗色彩色课程、底部房间号色盘示例、实际双指裁剪、固定署名和五次点击剪贴板；继续验证官网 23 项路由、成绩完整分页、考试发布与具体时间、校历、登录后自动课表采集、全屏 WebView、成绩未读/通知去重、缓存保留、一次性静默后台任务和暗色重建。

测试通过 importSchedule 导入合成文件，不调用应用内演示模式；应用已经完全删除该功能。旧测试实现可在对应历史分支查看。

仅在专门的自有模拟器使用。测试会删除该 App 测试背景、清空测试缓存、改变主题和设置、授予通知权限、写入合成成绩并强制一次符合条件的后台任务。没有学校账号或实际课程成绩，不要对日常 App 数据运行。官网网络失败的合成测试与受控页面不是实际账号同步成功的证据。

先用项目 build-offline.ps1 构建应用，再用同一签名构建测试：

```powershell
.\tests\android\build-runtime-test.ps1 -JdkPath $env:JAVA_HOME
adb -s emulator-5580 install -r ..\LUT-Schedule-0.1.9-preview.apk
adb -s emulator-5580 install -r .\tests\android\runtime-test-build\runtime-test.apk
adb -s emulator-5580 shell am instrument -w -r -e suite v019 cn.lut.schedule.tests/.RuntimeSmoke
```

自建版本默认签名位于 build/offline/preview-signing.p12；指定其他构建目录时传 -SigningKey，必须与 App 相同。公开仓库没有本任务交付 APK 的签名私钥；测试公开交付 APK 需要持有对应本地私钥。

成功需要三组 PASS。若自动化控制端限制单次运行时间，可分别添加 `-e phase design`、`-e phase protocol`、`-e phase regression` 执行，每一段都应返回 PASS 与 INSTRUMENTATION_CODE: -1。三个阶段分别覆盖 101、38、41 项断言，不把控制端超时算成通过。生产入口另行冷启动检查：

```powershell
adb -s emulator-5580 shell am force-stop cn.lut.schedule
adb -s emulator-5580 shell am start -W -n cn.lut.schedule/.BootstrapActivity
adb -s emulator-5580 logcat -d AndroidRuntime:E '*:S'
```

当前只有这一套入口；历史套件保存在对应 release 分支。实际结果见 runtime-0.1.9-result.txt。截图中课程均为合成导入数据，不包含学校账号或真实记录。
