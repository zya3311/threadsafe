package com.monitor.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class MonitorAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("Thread Monitor Agent is starting...");
        ClassFileTransformer transformer = new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if (className != null && 
                    (className.startsWith("com/model") || className.startsWith("com/monitor/agent/Test"))) {
                    ASMTransformer asmTransformer = new ASMTransformer();
                    return asmTransformer.transform(classfileBuffer);
                }
                return classfileBuffer;
            }
        };
        inst.addTransformer(transformer, true);
        System.out.println("Thread Monitor Agent started successfully");
    }
} 