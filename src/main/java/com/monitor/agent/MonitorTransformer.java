package com.monitor.agent;

import javassist.*;
import javassist.expr.*;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class MonitorTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {

        System.out.println("Transforming class: " + className);

        // 只处理com/model包下的类
        if (className == null || !className.startsWith("com/model")) {
            return null;
        }

        try {
            // 添加类加载器的类路径
            ClassPool cp = ClassPool.getDefault();
            if (loader != null) {
                cp.insertClassPath(new LoaderClassPath(loader));
            }

            // 获取类
            CtClass cc = null;
            try {
                cc = cp.get(className.replace('/', '.'));
                System.out.println("Successfully loaded class: " + className);
            } catch (NotFoundException e) {
                return null;
            }

            // 如果类已经被冻结，则解冻
            if (cc.isFrozen()) {
                cc.defrost();
            }

            // 排除接口和final类
            if (cc.isInterface() || Modifier.isFinal(cc.getModifiers())) {
                return null;
            }

            boolean modified = false;
            // 处理类中的所有字段
            for (CtField field : cc.getDeclaredFields()) {
                System.out.println("Processing field: " + field.getName());
                
                // 排除final字段
                if (Modifier.isFinal(field.getModifiers())) {
                    continue;
                }

                instrumentField(cc, field);
                modified = true;
            }

            if (modified) {
                System.out.println("Successfully transformed class: " + className);
                // 将转换后的字节码写入文件以供验证
                cc.writeFile("output");  // 确保output目录存在
            }

            return modified ? cc.toBytecode() : null;

        } catch (Exception e) {
            System.err.println("Error transforming class: " + className);
            e.printStackTrace();
        }
        return null;
    }

    private void instrumentField(CtClass cc, CtField field) throws CannotCompileException {
        String fieldName = field.getName();
        System.out.println("Instrumenting field: " + fieldName);
        
        // 构造监控代码
        String accessCode = String.format(
            "{ " +
            "System.out.println(\"Accessing field: %s.%s\"); " +
            "com.monitor.agent.AccessMonitor.checkAccess(\"%s\", \"%s\", Thread.currentThread()); " +
            "}",
            cc.getName(), fieldName, cc.getName(), fieldName
        );

        // 使用类级别的instrument
        cc.instrument(new ExprEditor() {
            public void edit(FieldAccess f) throws CannotCompileException {
                if (f.getFieldName().equals(fieldName)) {
                    System.out.println("Found field access: " + fieldName);
                    if (f.isReader()) {
                        System.out.println("Modifying read access");
                        f.replace(String.format(
                            "%s $_ = $proceed($$);",
                            accessCode
                        ));
                    } else if (f.isWriter()) {
                        System.out.println("Modifying write access");
                        f.replace(String.format(
                            "%s $proceed($$);",
                            accessCode
                        ));
                    }
                }
            }
        });
    }
} 