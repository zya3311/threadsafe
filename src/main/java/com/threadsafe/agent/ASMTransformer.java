package com.threadsafe.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ASMTransformer {
    public byte[] transform(byte[] classfileBuffer) {
        try {
            System.out.println("Starting ASM transformation");
            ClassReader cr = new ClassReader(classfileBuffer);
            
            System.out.println("Class access flags: " + cr.getAccess());
            System.out.println("Class version: " + cr.readByte(6) + "." + cr.readByte(7));
            
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
            FieldAccessVisitor fv = new FieldAccessVisitor(cw);
            cr.accept(fv, ClassReader.EXPAND_FRAMES);
            System.out.println("ASM transformation completed");
            return cw.toByteArray();
        } catch (Exception e) {
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