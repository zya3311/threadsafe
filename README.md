+ # Thread Safety Monitor
+ 
+ 一个基于Java Agent技术的线程安全监控工具，用于检测Java应用中的线程安全问题。
+ 
+ ## 功能特点
+ 
+ 1. 静态字段访问监控
+    - 监控非核心线程写入的静态字段
+    - 检测核心线程对这些字段的读取操作
+    - 自动记录访问位置和调用堆栈
+ 
+ 2. 注解支持
+    - 提供`@RsmThreadSafe`注解
+    - 可以标记在类、方法或字段上
+    - 被标记的元素将跳过线程安全检查
+ 
+ 3. 实时告警
+    - 发现违规访问时立即输出日志
+    - 包含完整的调用堆栈信息
+    - 记录线程名称和代码位置
+ 
+ ## 技术实现
+ 
+ 1. Java Agent
+    - 使用ASM库进行字节码增强
+    - 在字段访问指令处注入监控代码
+    - 支持类加载时和运行时的字节码修改
+ 
+ 2. 日志处理
+    - 使用Log4j2进行日志记录
+    - 支持不同级别的日志输出
+    - 提供堆栈信息的格式化展示
+ 
+ 3. 数据可视化
+    - 提供Python脚本处理日志数据
+    - 生成JSON格式的分析结果
+    - 支持多种可视化展示方式
+ 
+ ## 使用方法
+ 
+ 1. 构建Agent
+ ```bash
+ mvn clean package
+ ```
+ 
+ 2. 启动应用
+ ```bash
+ java -javaagent:thread-monitor-agent.jar -jar your-application.jar
+ ```
+ 
+ 3. 分析结果
+ ```bash
+ cd threadsafe/scripts
+ python convert_to_graphs.py
+ ```
+ 
+ ## 输出示例
+ 
+ 1. 日志输出
+ ```
+ [CONTRACT_WORKER] FATAL - Invalid read: CORE thread 'CONTRACT_WORKER' attempting to read variable static.com/citics/eqd/common/core/Global.warmup that was written by NON_CORE thread 'main'
+ [CONTRACT_WORKER] FATAL - Current thread stack trace: 
+     at com.citics.eqd.common.core.Global.canUpdate(Global.java:99)
+ [CONTRACT_WORKER] FATAL - Previous NON_CORE thread write stack trace: 
+     at com.citics.eqd.common.core.Global.<clinit>(Global.java:21)
+ ```
+ 
+ 2. JSON格式分析结果
+ ```json
+ {
+   "variable": "static.com/citics/eqd/common/core/Global.warmup",
+   "core_thread": {
+     "thread_name": "CONTRACT_WORKER",
+     "location": "com.citics.eqd.common.core.Global.canUpdate:line 99"
+   },
+   "non_core_thread": {
+     "thread_name": "main",
+     "location": "com.citics.eqd.common.core.Global.<clinit>:line 21"
+   }
+ }
+ ```
+ 
+ ## 工作原理
+ 
+ 1. 字节码增强
+    - 使用ASM访问者模式拦截字段访问
+    - 在字段访问前后注入监控代码
+    - 收集访问线程和堆栈信息
+ 
+ 2. 线程分类
+    - 核心线程：CONTRACT_WORKER
+    - 非核心线程：其他所有线程
+    - 基于线程名进行识别
+ 
+ 3. 违规检测
+    - 记录非核心线程的写操作
+    - 检测核心线程的读操作
+    - 发现违规时输出警告
+ 
+ ## 项目结构
+ 
+ ```
+ threadsafe/
+ ├── src/main/java/
+ │   └── com/threadsafe/agent/
+ │       ├── AccessMonitor.java      # 核心监控逻辑
+ │       ├── FieldAccessVisitor.java # 字节码访问者
+ │       └── MonitorAgent.java       # Agent入口
+ ├── scripts/
+ │   └── convert_to_graphs.py        # 日志分析脚本
+ └── output/
+     └── violations_simple.json      # 分析结果
+ ```
+ 
+ ## 注意事项
+ 
+ 1. 性能考虑
+    - 会对每个字段访问进行检查
+    - 建议仅在测试环境使用
+    - 可通过注解排除不需要检查的代码
+ 
+ 2. 使用限制
+    - 仅支持Java 8及以上版本
+    - 需要确保ASM版本与JDK版本兼容
+    - 某些JVM参数可能影响Agent的运行
+ 
+ ## 后续计划
+ 
+ 1. 功能增强
+    - 支持更多线程安全场景的检测
+    - 添加配置文件支持
+    - 提供更多可视化选项
+ 
+ 2. 性能优化
+    - 优化字节码注入逻辑
+    - 减少运行时开销
+    - 提供采样模式
+ 
+ 3. 使用体验
+    - 提供更友好的配置界面
+    - 优化警告信息的展示
+    - 添加更多统计分析功能