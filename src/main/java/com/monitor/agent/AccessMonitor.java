package com.monitor.agent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class AccessMonitor {
    // 存储变量访问信息的Map
    private static final Map<String, ThreadType> accessMap = new ConcurrentHashMap<>();

    public enum ThreadType {
        RSM,
        NON_RSM
    }

    public static void checkAccess(String className, String fieldName, Thread thread) {
        System.out.println("Checking access for " + className + "." + fieldName + " by thread " + thread.getName());
        String key = className + "." + fieldName;
        ThreadType currentThreadType = isRSMThread(thread) ? ThreadType.RSM : ThreadType.NON_RSM;

        ThreadType existingAccess = accessMap.get(key);
        if (existingAccess == null) {
            System.out.println("First access to " + key + " by " + currentThreadType + " thread");
            accessMap.put(key, currentThreadType);
        } else if (existingAccess != currentThreadType) {
            System.out.println("Invalid access to " + key + " by " + currentThreadType + " thread (previously accessed by " + existingAccess + ")");
            throw new IllegalStateException(
                    String.format("Variable %s was accessed by %s thread, but now being accessed by %s thread",
                            key, existingAccess, currentThreadType));
        }
    }

    private static boolean isRSMThread(Thread thread) {
        // 只有精确匹配 "RSM-Thread-" 开头的才是RSM线程
        return thread.getName().startsWith("RSM-Thread-");
    }

    // 用于测试的方法
    public static void clearAccessMap() {
        accessMap.clear();
    }

    public static Map<String, ThreadType> getAccessMap() {
        return new ConcurrentHashMap<>(accessMap);
    }
} 