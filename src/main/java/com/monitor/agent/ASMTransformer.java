package com.monitor.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ASMTransformer {
    public byte[] transform(byte[] classfileBuffer) {
        try {
            System.out.println("Starting ASM transformation");  // 添加日志
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            FieldAccessVisitor visitor = new FieldAccessVisitor(cw);
            cr.accept(visitor, 0);

            // 将生成的字节码写入文件
            byte[] bytecode = cw.toByteArray();
            writeBytecodeToFile(bytecode, "out/" + cr.getClassName() + ".class");

            System.out.println("ASM transformation completed");  // 添加日志
            return bytecode;
        } catch (Exception e) {
            System.err.println("Error during ASM transformation: " + e.getMessage());
            e.printStackTrace();
            return classfileBuffer;
        }
    }

    private void writeBytecodeToFile(byte[] bytecode, String filePath) {
        try {
            Files.createDirectories(Paths.get(filePath).getParent());
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(bytecode);
            }
            System.out.println("Bytecode written to: " + filePath);
        } catch (IOException e) {
            System.err.println("Failed to write bytecode to file: " + filePath);
            e.printStackTrace();
        }
    }
} 