package com.threadsafe.agent;

import org.objectweb.asm.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class FieldAccessVisitor extends ClassVisitor {
    private String className;
    private Map<String, Boolean> fieldAnnotations = new HashMap<>();

    public FieldAccessVisitor(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        className = name;
        System.out.println("Visiting class: " + className);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        System.out.println("Visiting field: " + name);
        return new FieldVisitor(Opcodes.ASM9, super.visitField(access, name, descriptor, signature, value)) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor.equals("Lcom/threadsafe/agent/annotation/AllowNonCoreRead;")) {
                    fieldAnnotations.put(name, true);
                }
                return super.visitAnnotation(descriptor, visible);
            }
        };
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        System.out.println("Visiting method: " + name);
        return new FieldAccessMethodVisitor(mv, className, name, fieldAnnotations);
    }
}

class FieldAccessMethodVisitor extends MethodVisitor {
    private static final Logger logger = LogManager.getLogger(FieldAccessMethodVisitor.class);
    private final String className;
    private final String methodName;
    private final Map<String, Boolean> fieldAnnotations;

    public FieldAccessMethodVisitor(MethodVisitor mv, String className, String methodName, Map<String, Boolean> fieldAnnotations) {
        super(Opcodes.ASM9, mv);
        this.className = className;
        this.methodName = methodName;
        this.fieldAnnotations = fieldAnnotations;
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        if (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD || 
            opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC) {
            
            if (methodName.equals("<init>")) {
                super.visitFieldInsn(opcode, owner, name, descriptor);
                return;
            }

            boolean isRead = (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC);
            boolean hasAllowNonCoreRead = fieldAnnotations.getOrDefault(name, false);

            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
            
            if (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD) {
                mv.visitVarInsn(Opcodes.ALOAD, 0);
            } else {
                mv.visitInsn(Opcodes.ACONST_NULL);
            }
            
            mv.visitLdcInsn(className);
            mv.visitLdcInsn(name);
            mv.visitInsn((opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC) ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
            mv.visitInsn(hasAllowNonCoreRead ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
            mv.visitInsn(isRead ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
            
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/threadsafe/agent/AccessMonitor", "checkAccess",
                    "(Ljava/lang/Thread;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;ZZZ)V", false);
        }
        super.visitFieldInsn(opcode, owner, name, descriptor);
    }
} 