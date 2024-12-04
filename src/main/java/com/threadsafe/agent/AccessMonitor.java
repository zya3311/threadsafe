package com.threadsafe.agent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AccessMonitor {
    private static final Map<String, ThreadType> accessMap = new ConcurrentHashMap<>();
    private static final Logger logger = LogManager.getLogger(AccessMonitor.class);

    public enum ThreadType {
        CORE,
        NON_CORE
    }

    public static void checkAccess(Thread thread, Object instance, String className, String fieldName, boolean isStatic, boolean hasAllowNonCoreRead, boolean isRead) {
        String key = isStatic ? 
            "static." + className + "." + fieldName :
            System.identityHashCode(instance) + "." + className + "." + fieldName;
        
        ThreadType currentThreadType = isCoreThread(thread) ? ThreadType.CORE : ThreadType.NON_CORE;

        if (currentThreadType == ThreadType.NON_CORE && hasAllowNonCoreRead && isRead) {
            logger.debug("Non-core thread {} allowed to read {} due to annotation", thread.getName(), key);
            return;
        }

        ThreadType existingAccess = accessMap.get(key);
        if (existingAccess == null) {
            logger.info("First access to {} by {} thread", key, currentThreadType);
            accessMap.put(key, currentThreadType);
        } else if (existingAccess != currentThreadType) {
            logger.error("Invalid access to {} by {} thread (previously accessed by {})",
                key, currentThreadType, existingAccess);
            throw new IllegalStateException(
                    String.format("Variable %s was accessed by %s thread, but now being accessed by %s thread",
                            key, existingAccess, currentThreadType));
        }
    }

    private static boolean isCoreThread(Thread thread) {
        return thread.getName().equals("CONTRACT_WORKER");
    }

    // 用于测试的方法
    public static void clearAccessMap() {
        accessMap.clear();
    }

    public static Map<String, ThreadType> getAccessMap() {
        return new ConcurrentHashMap<>(accessMap);
    }
} 