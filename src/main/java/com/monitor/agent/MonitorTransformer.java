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

        // 扩展排除规则
        if (className == null ||
                className.startsWith("java/") ||
                className.startsWith("sun/") ||
                className.startsWith("jdk/") ||
                className.startsWith("com/sun/") ||
                className.startsWith("com/monitor/") ||
                className.contains("ClassLoader") ||
                className.contains("javassist") ||
                className.contains("asm")) {
            return null;
        }

        try {
            // 添加类加载器的类路径
            ClassPool cp = ClassPool.getDefault();
            if (loader != null) {
                cp.insertClassPath(new LoaderClassPath(loader));
            }

            // 检查类是否已经被加载
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
                // 排除final字段
                if (Modifier.isFinal(field.getModifiers())) {
                    continue;
                }

                instrumentField(cc, field);
                modified = true;
            }

            if (modified) {
                System.out.println("Successfully transformed class: " + className);
                System.out.println("Generated methods for fields: " + cc.getDeclaredFields().length);
            }

            return modified ? cc.toBytecode() : null;

        } catch (Exception e) {
            System.err.println("Error transforming class: " + className);
            e.printStackTrace();
        }
        return null;
    }

    private void instrumentField(CtClass cc, CtField field) throws CannotCompileException, NotFoundException {
        String fieldName = field.getName();

        // 添加字段访问监控代码
        String accessCode = String.format("com.monitor.agent.AccessMonitor.checkAccess(\"%s\", \"%s\", Thread.currentThread());",
                cc.getName(), fieldName);

        field.setModifiers(field.getModifiers() & ~Modifier.PRIVATE);  // 移除private修饰符
        cc.instrument(new ExprEditor() {
            public void edit(FieldAccess f) throws CannotCompileException {
                if (f.getFieldName().equals(fieldName)) {
                    f.replace(String.format("{ %s $_ = $proceed($$); }", accessCode));
                }
            }
        });
    }

    private String capitalize(String str) {
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
} 