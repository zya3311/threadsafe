package com.monitor.test;

import com.monitor.agent.AccessMonitor;
import org.junit.Before;
import org.junit.Test;

public class ThreadMonitorTest {
    
    private TestClass testObj;
    
    @Before
    public void setup() {
        testObj = new TestClass();
        AccessMonitor.clearAccessMap();
    }
    
    @Test
    public void testRSMThreadAccess() throws InterruptedException {
        // 创建一个RSM线程
        Thread rsmThread = new Thread(() -> {
            testObj.setValue(100);
            testObj.getValue();
        }, "RSM-Thread-1");
        
        rsmThread.start();
        rsmThread.join();
        
        // 再次用RSM线程访问应该不会抛出异常
        Thread anotherRSMThread = new Thread(() -> {
            testObj.setValue(200);
        }, "RSM-Thread-2");
        
        anotherRSMThread.start();
        anotherRSMThread.join();
    }
    
    @Test(expected = IllegalStateException.class)
    public void testMixedThreadAccess() throws InterruptedException {
        // 首先用RSM线程访问
        Thread rsmThread = new Thread(() -> {
            testObj.setValue(100);
        }, "RSM-Thread-1");
        
        rsmThread.start();
        rsmThread.join();
        
        // 然后用非RSM线程访问，应该抛出异常
        Thread nonRSMThread = new Thread(() -> {
            testObj.setValue(200);
        }, "Worker-Thread-1");
        
        nonRSMThread.start();
        nonRSMThread.join();
    }
    
    @Test
    public void testNonRSMThreadAccess() throws InterruptedException {
        // 创建两个非RSM线程
        Thread nonRSMThread1 = new Thread(() -> {
            testObj.setValue(100);
            testObj.getValue();
        }, "Worker-Thread-1");
        
        Thread nonRSMThread2 = new Thread(() -> {
            testObj.setValue(200);
        }, "Worker-Thread-2");
        
        nonRSMThread1.start();
        nonRSMThread1.join();
        
        // 同样是非RSM线程访问，不应该抛出异常
        nonRSMThread2.start();
        nonRSMThread2.join();
    }
    
    @Test
    public void testMultipleVariables() throws InterruptedException {
        // 测试不同变量可以被不同类型的线程访问
        Thread rsmThread = new Thread(() -> {
            testObj.setValue(100); // value变量被RSM线程访问
        }, "RSM-Thread-1");
        
        Thread nonRSMThread = new Thread(() -> {
            testObj.setName("test"); // name变量被非RSM线程访问
        }, "Worker-Thread-1");
        
        rsmThread.start();
        nonRSMThread.start();
        
        rsmThread.join();
        nonRSMThread.join();
    }
} 