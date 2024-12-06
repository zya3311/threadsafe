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
    private static String targetPackage ="com/threadsafe";
    private static final Set<String> excludedPrefixes = new HashSet<>(Arrays.asList(
        "com/threadsafe/agent",  // 已有的排除
        "com/example/excluded"   // 新增的排除
    ));

    public static void premain(String agentArgs, Instrumentation inst) {
        logger.info("Thread Monitor Agent is starting...");
        logger.info("Current classpath: {}", System.getProperty("java.class.path"));
        if (agentArgs != null && !agentArgs.trim().isEmpty()) {
            targetPackage = agentArgs.split(",") [0].replace(".", "/");
            logger.info("Monitoring packages: {}", targetPackage);
        }

        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className,
                                    Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain,
                                    byte[] classfiledBuffer) {
                try {
//                    logger.info("Transform called for class: {} with classloader: {}", className, loader);
                    if (className != null && className.startsWith(targetPackage) && !excludedPrefixes.stream().anyMatch(prefix -> className.startsWith(prefix))) {
//                        logger.debug("Transforming class: {}", className);
                        ASMTransformer asmTransformer = new ASMTransformer();
                        return asmTransformer.transform(classfiledBuffer);
                    }
                } catch (Throwable t) {
                    logger.error("Error transforming class: " + className, t);
                }
                return classfiledBuffer;
            }
        }, true);

//        try {
//            Class<?>[] loadedClasses = inst.getAllLoadedClasses();
//            for (Class<?> clazz : loadedClasses) {
//                String className = clazz.getName().replace('.', '/');
//                if (className.equals("com/threadsafe/Model") ||
//                        className.equals("com/threadsafe/Test")) {
//                    if (inst.isModifiableClass(clazz) && inst.isRetransformClassesSupported()) {
//                        logger.info("Retransforming loaded class: {}", clazz.getName());
//                        inst.retransformClasses(clazz);
//                    }
//                }
//            }
//        } catch (Exception e) {
//            logger.error("Error retransforming classes", e);
//        }

        logger.info("Thread Monitor Agent started successfully");
    }
} 