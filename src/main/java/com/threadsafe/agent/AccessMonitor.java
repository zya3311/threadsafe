package com.threadsafe.agent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

public class AccessMonitor {
    private static final Map<String, WriteInfo> nonCoreWriteMap = new ConcurrentHashMap<>();
    private static final Set<String> checkedFields = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Logger logger = LogManager.getLogger(AccessMonitor.class);
    private static final Map<String, Set<String>> ignoreStaticInitFields = new ConcurrentHashMap<>();

    public enum ThreadType {
        CORE,
        NON_CORE
    }

    private static class WriteInfo {
        final String threadName;
        final Exception stackTrace;
        final boolean isStaticInit;

        WriteInfo(String threadName, Exception stackTrace, boolean isStaticInit) {
            this.threadName = threadName;
            this.stackTrace = stackTrace;
            this.isStaticInit = isStaticInit;
        }
    }

    private static class StackNode {
        String method;
        String clazz;
        List<StackNode> children = new ArrayList<>();
        
        public StackNode(String method, String clazz) {
            this.method = method;
            this.clazz = clazz;
        }
        
        public static StackNode fromStackTrace(StackTraceElement[] elements) {
            StackNode root = new StackNode("root", "");
            StackNode current = root;
            for (StackTraceElement element : elements) {
                String simpleName = element.getClassName();
                int lastDot = simpleName.lastIndexOf('.');
                if (lastDot > 0) {
                    simpleName = simpleName.substring(lastDot + 1);
                }
                
                StackNode node = new StackNode(
                    element.getMethodName(),
                    simpleName
                );
                current.children.add(node);
                current = node;
            }
            return root;
        }
    }

    private static String convertToFlameGraph(StackNode currentStack, StackNode previousStack) {
        StringBuilder json = new StringBuilder();
        json.append("{\"current\":");
        convertNodeToJson(currentStack, json);
        json.append(",\"previous\":");
        convertNodeToJson(previousStack, json);
        json.append("}");
        return json.toString();
    }

    private static void convertNodeToJson(StackNode node, StringBuilder json) {
        json.append("{\"m\":\"").append(node.method)
            .append("\",\"c\":\"").append(node.clazz)
            .append("\",\"n\":[");
        for (int i = 0; i < node.children.size(); i++) {
            if (i > 0) json.append(",");
            convertNodeToJson(node.children.get(i), json);
        }
        json.append("]}");
    }

    public static void checkAccess(Thread thread, Object instance, String className, String fieldName, boolean isStatic, boolean isRead) {
        // 生成key，如果是静态变量，则key为"static." + className + "." + fieldName，否则为thread.getName() + "." + System.identityHashCode(instance) + "." + className + "." + fieldName
        String key = isStatic ?
            "static." + className + "." + fieldName :
            thread.getName() + "." + System.identityHashCode(instance) + "." + className + "." + fieldName;

        // 如果已经检查过该变量，则直接返回
        if (checkedFields.contains(key)) {
            return;
        }

        // 判断当前线程类型，如果是核心线程，则类型为CORE，否则为NON_CORE
        ThreadType currentThreadType = isCoreThread(thread) ? ThreadType.CORE : ThreadType.NON_CORE;

        // 如果是读取操作
        if (isRead) {
            // 获取该变量的写信息
            WriteInfo writeInfo = nonCoreWriteMap.get(key);
            // 如果当前线程是核心线程，并且该变量被非核心线程写入过
            if (currentThreadType == ThreadType.CORE && writeInfo != null) {
                // 如果该变量是静态初始化的，并且需要忽略静态初始化，则忽略
                boolean shouldIgnore = writeInfo.isStaticInit && shouldIgnoreStaticInit(className, fieldName);
                if (!shouldIgnore) {
                    // 记录错误日志
                    logger.fatal("Invalid read: CORE thread '{}' attempting to read variable {} that was written by NON_CORE thread '{}'",
                        thread.getName(), key, writeInfo.threadName);
                    logger.fatal("Current thread stack trace: {}", formatStackTrace(new Exception("Stack trace")));
                    logger.fatal("Previous NON_CORE thread write stack trace: {}", formatStackTrace(writeInfo.stackTrace));
                    // 将该变量标记为已检查
                    checkedFields.add(key);
                }
            }
        } else {
            // 如果是写入操作，并且当前线程是非核心线程
            if (currentThreadType == ThreadType.NON_CORE) {
                // 判断是否是静态初始化
                boolean isStaticInit = isStaticInitialization(Thread.currentThread().getStackTrace());
                // 将该变量的写信息存入nonCoreWriteMap
                nonCoreWriteMap.put(key, new WriteInfo(
                    thread.getName(), 
                    new Exception("NON_CORE thread write stack trace"),
                    isStaticInit
                ));
            }
        }
    }

    private static String formatStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder("\n");
        StackTraceElement[] stackTrace = e.getStackTrace();
        for (StackTraceElement element : stackTrace) {
            sb.append("    at ").append(element).append("\n");
        }
        return sb.toString();
    }

    private static boolean isCoreThread(Thread thread) {
        return thread.getName().equals("CONTRACT_WORKER");
    }

    public static void clearAccessMap() {
        nonCoreWriteMap.clear();
        checkedFields.clear();
    }

    public static Map<String, WriteInfo> getAccessMap() {
        return new ConcurrentHashMap<>(nonCoreWriteMap);
    }

    public static void setIgnoreStaticInitFields(String className, Set<String> fields) {
        ignoreStaticInitFields.put(className, fields);
    }

    private static boolean shouldIgnoreStaticInit(String className, String fieldName) {
        Set<String> fields = ignoreStaticInitFields.get(className);
        return fields != null && fields.contains(fieldName);
    }

    private static boolean isStaticInitialization(StackTraceElement[] stackTrace) {
        for (StackTraceElement element : stackTrace) {
            if (element.getMethodName().equals("<clinit>")) {
                return true;
            }
        }
        return false;
    }
} 