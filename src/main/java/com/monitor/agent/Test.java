package com.monitor.agent;

import com.model.Model;

public class Test {
    public static void main(String[] args) {
        try {
            Model model = new Model();
            // 首先用RSM线程访问
            Thread rsmThread = new Thread(() -> {
                model.value = 100;
            }, "RSM-Thread-1");

            rsmThread.start();
            rsmThread.join();

            // 然后用非RSM线程访问，应该抛出异常
            Thread nonRSMThread = new Thread(() -> {
                model.value = 200;
            }, "Worker-Thread-1");

            nonRSMThread.start();
            nonRSMThread.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
