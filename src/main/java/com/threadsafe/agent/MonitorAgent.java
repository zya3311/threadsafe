package com.threadsafe.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MonitorAgent {
    private static final Logger logger = LogManager.getLogger(MonitorAgent.class);
    private static Set<String> targetPackages = new HashSet<>();

    public static void premain(String agentArgs, Instrumentation inst) {
        logger.info("Thread Monitor Agent is starting...");
        logger.info("Current classpath: {}", System.getProperty("java.class.path"));
        
        if (agentArgs != null && !agentArgs.trim().isEmpty()) {
            String[] packages = agentArgs.split(",");
            targetPackages.addAll(Arrays.asList(packages));
            logger.info("Monitoring packages: {}", targetPackages);
        } else {
            targetPackages.add("com/model");
            logger.info("No packages specified, monitoring default package: com/model");
        }

        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className,
                                  Class<?> classBeingRedefined,
                                  ProtectionDomain protectionDomain,
                                  byte[] classfileBuffer) {
                try {
                    logger.info("Transform called for class: {} with classloader: {}", className, loader);
                    if (className != null && (className.equals("com/model/Model") ||
                        className.equals("com/threadsafe/Test"))) {
                        logger.debug("Transforming class: {}", className);
                        ASMTransformer asmTransformer = new ASMTransformer();
                        return asmTransformer.transform(classfileBuffer);
                    }
                } catch (Throwable t) {
                    logger.error("Error transforming class: " + className, t);
                }
                return classfileBuffer;
            }
        }, true);
        
        try {
            Class<?>[] loadedClasses = inst.getAllLoadedClasses();
            for (Class<?> clazz : loadedClasses) {
                String className = clazz.getName().replace('.', '/');
                if (className.equals("com/model/Model") || 
                    className.equals("com/threadsafe/Test")) {
                    if (inst.isModifiableClass(clazz) && inst.isRetransformClassesSupported()) {
                        logger.info("Retransforming loaded class: {}", clazz.getName());
                        inst.retransformClasses(clazz);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error retransforming classes", e);
        }
        
        logger.info("Thread Monitor Agent started successfully");
    }

    private static boolean shouldTransform(String className) {
        if (className.startsWith("com/threadsafe/agent")) {
            logger.debug("Skipping agent's own class: {}", className);
            return false;
        }
        
        return targetPackages.stream().anyMatch(pkg -> 
            className.startsWith(pkg.replace('.', '/'))
        );
    }
} 