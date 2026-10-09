# Android 合成集成测试

当前入口 `suite=v018` 依次运行 RuntimeV017、RuntimeV018、RuntimeV016。覆盖官网 23 项直达路由、完整成绩分页、考试发布与时间、课表和校历查询、登录后自动课表路由、全屏 WebView；实际双指触摸手势、三份背景裁剪/导出/原子保存/取消/EXIF 布局恢复、三页共用和切换、课程色盘即时示例；本地有界脱敏报告、源码链接、错误弹窗/设置滚动/剪贴板/恢复标志；成绩基线/变化/去重/未读、Android 通知和顶部提示、官网小节、缓存保留、一次性静默后台任务、暗色重建。

仅在专门的自有模拟器使用。测试会删除该 App 测试背景、清空测试缓存、改变主题和设置、授予通知权限、写入合成成绩并强制一次符合条件的后台任务。没有学校账号或实际课程成绩，不要对日常 App 数据运行。官网网络失败的合成测试与受控页面不是实际账号同步成功的证据。

先用项目 build-offline.ps1 构建应用，再用同一签名构建测试：

```powershell
.\tests\android\build-runtime-test.ps1 -JdkPath $env:JAVA_HOME
adb -s emulator-5580 install -r ..\LUT-Schedule-0.1.8-preview.apk
adb -s emulator-5580 install -r .\tests\android\runtime-test-build\runtime-test.apk
adb -s emulator-5580 shell am instrument -w -r -e suite v018 cn.lut.schedule.tests/.RuntimeSmoke
```

自建版本默认签名位于 build/offline/preview-signing.p12；指定其他构建目录时传 -SigningKey，必须与 App 相同。公开仓库没有本任务交付 APK 的签名私钥；测试公开交付 APK 需要持有对应本地私钥。

成功需要三组 PASS 且 INSTRUMENTATION_CODE: -1。生产入口另行冷启动并等待网络超时：

```powershell
adb -s emulator-5580 shell am force-stop cn.lut.schedule
adb -s emulator-5580 shell am start -W -n cn.lut.schedule/.BootstrapActivity
adb -s emulator-5580 logcat -d AndroidRuntime:E '*:S'
```

旧默认/academic 入口保留作历史参考，不是当前回归入口；v018-ui 是新界面的独立定位入口。已保存的实际测试结果见 runtime-0.1.8-result.txt。
