package com.threadsafe;

public class Test {
    public static void main(String[] args) {
        try {
            // 添加调试信息
            System.out.println("Model class location: " + Model.class.getProtectionDomain().getCodeSource().getLocation());
            System.out.println("Model class loader: " + Model.class.getClassLoader());
            
            Model model1 = new Model();
            // 首先用CORE线程访问
            Thread coreThread = new Thread(() -> {
                model1.value = 100;
                Model.name = "zzz";
                int a = Model.staticValue;
                model1.setV(1);
            }, "CONTRACT_WORKER");

            coreThread.start();
            coreThread.join();
            Model model2 = new Model();
            // 然后用非CORE线程访问，应该抛出异常
            Thread nonCoreThread = new Thread(() -> {
                model2.value = 200;
                int a = Model.staticValue;

//                Model.name = "yyy";
                int v = model1.getV();
            }, "Worker-Thread-1");

            nonCoreThread.start();
            nonCoreThread.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
