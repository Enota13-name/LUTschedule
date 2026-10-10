# 同步优先级回调基准

此目录用于开发测试，不属于生产 App。脚本直接读取并编译仓库现有的 `SyncCoordinator.java`，再在 `build/` 内生成一个隔离原型：课表校验并成功保存后立即发出独立的 `timetable-ready` 回调，随后继续成绩、考试和辅助模块。不会改动生产源码。

在仓库根目录运行：

```powershell
python tests/sync-priority/run_benchmark.py
```

脚本依次从 `JAVA_HOME`、PATH 查找 JDK，最后尝试本机路径 `D:\Android\jbr`。运行会重新生成 `tests/sync-priority/build/`，结果写到 `build/results.json`。删除前会解析并验证路径的父目录正是本目录且目录名为 `build`。`baseline-v1.0.1.json` 是已记录的固定合成结果，不会被运行覆盖。

每次编译和 Java 执行最多等待 30 秒。本次电脑的 PATH 指向 Oracle Java 启动垫片，直接使用它会挂起；验证时在测试命令的进程环境指定了已有 JDK `D:\Android\jbr`，没有修改系统环境或网络设置。如果遇到相同情况，请在测试进程中指定可用的 `JAVA_HOME`。此脚本面向 v1.0.1；生产协调器完成优化后，三处原型补丁锚点可能不再适用，需相应更新测试。

基准用虚拟时钟，不等待真实时间。受控延迟为课表 1,000 ms、成绩 3,000 ms、考试 4,000 ms、每个辅助模块 2,000 ms。当前固定结果中，旧流程在合成时刻 1,000 ms 保存课表，到 18,000 ms 才调用最终回调；隔离原型在 1,000 ms 发出课表就绪回调，最终回调仍在 18,000 ms。该数字体现回调顺序和测试中设定的等待，不能当作真实官网速度或实际提速。

测试用假 Reader 和假 Store 推进生产协调器代码；它们不运行真实解析器、Android SQLite，也不测磁盘读写。取消测试通过在 `close()` 后手动投递旧课表回调来检查票据保护，取消模型经过简化。此基准不渲染真实 Android 页面，因此 `timetable-ready` 回调时间不是图标可见时间、Android 首帧或 UI 实测。结果也不代表当前 APK 已经采用该原型或变快。
