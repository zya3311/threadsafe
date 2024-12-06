package com.threadsafe.agent;

import org.objectweb.asm.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.HashMap;
import java.util.Map;

public class FieldAccessVisitor extends ClassVisitor {
    private static final Logger logger = LogManager.getLogger(FieldAccessVisitor.class);
    private String className;
    private Map<String, Integer> fieldAccess = new HashMap<>();
    private boolean isExcluded = false;

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
        if (isExcluded) {
            return super.visitField(access, name, descriptor, signature, value);
        }
        System.out.println("Visiting field: " + name);
        String key = className + "#" + name;
        fieldAccess.put(key, access);
        
        return new FieldVisitor(Opcodes.ASM9, super.visitField(access, name, descriptor, signature, value)) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor.equals("Lcom/threadsafe/agent/annotation/RsmThreadSafe;")) {
                    logger.info("Field {} in class {} is excluded from thread safety check due to @RsmThreadSafe", name, className);
                    fieldAccess.put(key, access | Opcodes.ACC_FINAL);  // 标记为final，这样就会跳过检查
                }
                return super.visitAnnotation(descriptor, visible);
            }
        };
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (isExcluded) {
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        System.out.println("Visiting method: " + name);
        return new FieldAccessMethodVisitor(mv, className, name, fieldAccess);
    }
}

class FieldAccessMethodVisitor extends MethodVisitor {
    private static final Logger logger = LogManager.getLogger(FieldAccessMethodVisitor.class);
    private final String className;
    private final String methodName;
    private final Map<String, Integer> fieldAccess;

    public FieldAccessMethodVisitor(MethodVisitor mv, String className, String methodName, Map<String, Integer> fieldAccess) {
        super(Opcodes.ASM9, mv);
        this.className = className;
        this.methodName = methodName;
        this.fieldAccess = fieldAccess;
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        if (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD || 
            opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC) {
            
            if (methodName.equals("<init>")) {
                super.visitFieldInsn(opcode, owner, name, descriptor);
                return;
            }

            String key = owner + "#" + name;
            Integer access = fieldAccess.get(key);
            if (access != null && (access & Opcodes.ACC_FINAL) != 0) {
                super.visitFieldInsn(opcode, owner, name, descriptor);
                return;
            }

            boolean isRead = (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC);
            
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
            
            if (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD) {
                mv.visitVarInsn(Opcodes.ALOAD, 0);
            } else {
                mv.visitInsn(Opcodes.ACONST_NULL);
            }
            
            mv.visitLdcInsn(className);
            mv.visitLdcInsn(name);
            mv.visitInsn((opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC) ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
            mv.visitInsn(isRead ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
            
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/threadsafe/agent/AccessMonitor", "checkAccess",
                    "(Ljava/lang/Thread;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;ZZ)V", false);
        }
        super.visitFieldInsn(opcode, owner, name, descriptor);
    }
} 