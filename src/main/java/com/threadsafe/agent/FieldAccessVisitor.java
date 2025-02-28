package com.threadsafe.agent;

import org.objectweb.asm.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public class FieldAccessVisitor extends ClassVisitor {
    private static final Logger logger = LogManager.getLogger(FieldAccessVisitor.class);
    private String className;
    private Map<String, Integer> fieldAccess = new HashMap<>();
    private boolean isExcluded = false;
    private Set<String> ignoreStaticInitFields = new HashSet<>();

    public FieldAccessVisitor(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        // 检查是否有@RsmThreadSafe注解
        if (descriptor.equals("Lcom/threadsafe/agent/annotation/RsmThreadSafe;")) {
            isExcluded = true;
            logger.info("Class {} is excluded from thread safety check due to @RsmThreadSafe", className);
        }
        return super.visitAnnotation(descriptor, visible);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        // 如果isExcluded为true，则调用父类的visitField方法
        if (isExcluded) {
            return super.visitField(access, name, descriptor, signature, value);
        }
        // 打印正在访问的字段名
        System.out.println("Visiting field: " + name);
        // 生成字段访问的key
        String key = className + "#" + name;
        // 将字段访问权限存入fieldAccess中
        fieldAccess.put(key, access);
        
        // 返回一个新的FieldVisitor对象，该对象继承自父类的visitField方法
        return new FieldVisitor(Opcodes.ASM9, super.visitField(access, name, descriptor, signature, value)) {
            // 重写visitAnnotation方法
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                // 如果注解描述符等于RsmThreadSafe，则标记为final，这样就会跳过检查
                if (descriptor.equals("Lcom/threadsafe/agent/annotation/RsmThreadSafe;")) {
                    logger.info("Field {} in class {} is excluded from thread safety check due to @RsmThreadSafe", name, className);
                    fieldAccess.put(key, access | Opcodes.ACC_FINAL);  // 标记为final，这样就会跳过检查
                }
                // 如果注解描述符等于IgnoreStaticInit，则添加到忽略静态初始化字段列表中
                if (descriptor.equals("Lcom/threadsafe/agent/annotation/IgnoreStaticInit;")) {
                    ignoreStaticInitFields.add(name);
                    logger.info("Field {} in class {} will ignore static initialization writes", 
                        name, className);
                }
                // 调用父类的visitAnnotation方法
                return super.visitAnnotation(descriptor, visible);
            }
        };
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        System.out.println("Visiting method: " + name);
        // 创建一个包装的MethodVisitor来检查方法的注解
        return new MethodVisitor(Opcodes.ASM9, 
               new FieldAccessMethodVisitor(mv, className, name, fieldAccess, isExcluded)) {
            private boolean isMethodExcluded = false;

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor.equals("Lcom/threadsafe/agent/annotation/RsmThreadSafe;")) {
                    isMethodExcluded = true;
                    logger.info("Method {} in class {} is excluded from thread safety check due to @RsmThreadSafe", 
                        name, className);
                }
                return super.visitAnnotation(descriptor, visible);
            }

            @Override
            public void visitCode() {
                ((FieldAccessMethodVisitor)mv).setMethodExcluded(isMethodExcluded);
                super.visitCode();
            }
        };
    }

    @Override
    public void visitEnd() {
        // 将信息传递给AccessMonitor
        AccessMonitor.setIgnoreStaticInitFields(className, ignoreStaticInitFields);
        super.visitEnd();
    }
}

class FieldAccessMethodVisitor extends MethodVisitor {
    private static final Logger logger = LogManager.getLogger(FieldAccessMethodVisitor.class);
    private final String className;
    private final String methodName;
    private final Map<String, Integer> fieldAccess;
    private final boolean isClassExcluded;
    private boolean isMethodExcluded;

    public FieldAccessMethodVisitor(MethodVisitor mv, String className, String methodName, 
                                  Map<String, Integer> fieldAccess, boolean isClassExcluded) {
        super(Opcodes.ASM9, mv);
        this.className = className;
        this.methodName = methodName;
        this.fieldAccess = fieldAccess;
        this.isClassExcluded = isClassExcluded;
        this.isMethodExcluded = false;
    }

    public void setMethodExcluded(boolean excluded) {
        this.isMethodExcluded = excluded;
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        // 如果操作码是GETFIELD、PUTFIELD、GETSTATIC或PUTSTATIC
        if (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD ||
            opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC) {
            
            // 如果类或方法被标记为@RsmThreadSafe，跳过检查
            if (isClassExcluded || isMethodExcluded) {
                super.visitFieldInsn(opcode, owner, name, descriptor);
                return;
            }

            String key = owner + "#" + name;
            // 获取字段的访问权限
            Integer access = fieldAccess.get(key);
            // 如果字段被标记为final，跳过检查
            if (access != null && (access & Opcodes.ACC_FINAL) != 0) {
                super.visitFieldInsn(opcode, owner, name, descriptor);
                return;
            }

            // 判断是读操作还是写操作
            boolean isRead = (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC);
            
            // 调用Thread.currentThread()方法获取当前线程
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
            
            // 如果是字段操作，将当前对象作为参数传递
            if (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD) {
                mv.visitVarInsn(Opcodes.ALOAD, 0);
            } else {
                // 如果是静态字段操作，将null作为参数传递
                mv.visitInsn(Opcodes.ACONST_NULL);
            }
            
            // 将字段所属类名、字段名、是否是静态字段、是否是读操作作为参数传递
            mv.visitLdcInsn(owner);
            mv.visitLdcInsn(name);
            mv.visitInsn((opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC) ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
            mv.visitInsn(isRead ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
            
            // 调用AccessMonitor.checkAccess方法进行访问检查
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/threadsafe/agent/AccessMonitor", "checkAccess",
                    "(Ljava/lang/Thread;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;ZZ)V", false);
        }
        // 如果不是字段操作，调用父类的方法
        super.visitFieldInsn(opcode, owner, name, descriptor);
    }
} 