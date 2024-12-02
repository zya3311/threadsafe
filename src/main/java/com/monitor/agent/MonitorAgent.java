package com.monitor.agent;

import java.lang.instrument.Instrumentation;

public class MonitorAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("Thread Monitor Agent is starting...");
        try {
            inst.addTransformer(new MonitorTransformer());
            System.out.println("Thread Monitor Agent started successfully");
        } catch (Exception e) {
            System.err.println("Failed to start Thread Monitor Agent");
            e.printStackTrace();
        }
    }
} 