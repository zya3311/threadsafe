package com.monitor.agent;

import org.objectweb.asm.*;

public class FieldAccessVisitor extends ClassVisitor {
    private String className;

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
        return super.visitField(access, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        System.out.println("Visiting method: " + name);
        return new FieldAccessMethodVisitor(mv, className);
    }
}

class FieldAccessMethodVisitor extends MethodVisitor {
    private final String className;

    public FieldAccessMethodVisitor(MethodVisitor mv, String className) {
        super(Opcodes.ASM9, mv);
        this.className = className;
        System.out.println("Created FieldAccessMethodVisitor for class: " + className);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        System.out.println("visitFieldInsn called: opcode=" + opcode + ", owner=" + owner + ", name=" + name);
        if (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD) {
            System.out.println("Inserting access check for field: " + name + " in class: " + className);
            
            mv.visitLdcInsn(className);
            mv.visitLdcInsn(name);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/monitor/agent/AccessMonitor", "checkAccess",
                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Thread;)V", false);
        }
        super.visitFieldInsn(opcode, owner, name, descriptor);
    }
} 