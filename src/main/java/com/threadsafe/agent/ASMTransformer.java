package com.threadsafe.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.File;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ASMTransformer {
    private static final String OUTPUT_DIR = "out/transformed_classes";
    private static final Logger logger = LogManager.getLogger(ASMTransformer.class);

    public byte[] transform(byte[] classfileBuffer) {
        try {
            System.out.println("Starting ASM transformation");
            ClassReader cr = new ClassReader(classfileBuffer);
            
            System.out.println("Class access flags: " + cr.getAccess());
            System.out.println("Class version: " + cr.readByte(6) + "." + cr.readByte(7));
            
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
            FieldAccessVisitor fv = new FieldAccessVisitor(cw);
            cr.accept(fv, ClassReader.EXPAND_FRAMES);
            
            byte[] transformedClass = cw.toByteArray();

            // 添加保存class文件的功能
            try {
                String rootDir = new File(System.getProperty("user.dir")).getAbsolutePath();
                String outputDir = new File(rootDir, OUTPUT_DIR).getAbsolutePath();
                saveClassFile(outputDir, cr.getClassName(), transformedClass);
            } catch (Exception e) {
                logger.error("Failed to save class file: {}", e.getMessage());
            }
            // todo:

            System.out.println("ASM transformation completed");
            return transformedClass;
        } catch (Exception e) {
            e.printStackTrace();
            return classfileBuffer;
        }
    }

    private void saveClassFile(String baseDir, String className, byte[] classData) {
        try {
            File dir = new File(baseDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String fileName = className + ".class";
            File outputFile = new File(dir, fileName);

            File parent = outputFile.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(classData);
            }

            System.out.println("Saved transformed class to: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Error saving class file: " + e.getMessage(), e);
        }
    }
} 