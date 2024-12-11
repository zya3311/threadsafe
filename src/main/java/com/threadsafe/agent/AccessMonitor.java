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
    private static final int MAX_STACK_DEPTH = 10;

    public enum ThreadType {
        CORE,
        NON_CORE
    }

    private static class WriteInfo {
        final String threadName;
        final Exception stackTrace;

        WriteInfo(String threadName, Exception stackTrace) {
            this.threadName = threadName;
            this.stackTrace = stackTrace;
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
        String key = isStatic ? 
            "static." + className + "." + fieldName :
            thread.getName() + "." + System.identityHashCode(instance) + "." + className + "." + fieldName;

        if (checkedFields.contains(key)) {
            return;
        }

        ThreadType currentThreadType = isCoreThread(thread) ? ThreadType.CORE : ThreadType.NON_CORE;

        if (isRead) {
            WriteInfo writeInfo = nonCoreWriteMap.get(key);
            if (currentThreadType == ThreadType.CORE && writeInfo != null) {
                logger.fatal("Invalid read: CORE thread '{}' attempting to read variable {} that was written by NON_CORE thread '{}'", 
                    thread.getName(), key, writeInfo.threadName);
                logger.fatal("Current thread stack trace: {}", formatStackTrace(new Exception("Stack trace")));
                logger.fatal("Previous NON_CORE thread write stack trace: {}", formatStackTrace(writeInfo.stackTrace));
                checkedFields.add(key);
            }
        } else {
            if (currentThreadType == ThreadType.NON_CORE) {
                nonCoreWriteMap.put(key, new WriteInfo(
                    thread.getName(), 
                    new Exception("NON_CORE thread write stack trace")
                ));
            }
        }
    }

    private static String formatStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder("\n");
        StackTraceElement[] stackTrace = e.getStackTrace();
        int depth = Math.min(stackTrace.length, MAX_STACK_DEPTH);
        
        for (int i = 0; i < depth; i++) {
            sb.append("    at ").append(stackTrace[i]).append("\n");
        }
        
        if (stackTrace.length > MAX_STACK_DEPTH) {
            sb.append("    ... ").append(stackTrace.length - MAX_STACK_DEPTH).append(" more");
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
} 