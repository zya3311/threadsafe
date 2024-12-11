package com.threadsafe;

public class Model {
    public int value;

    public static String name;

    public static final int staticValue = 100;

    private int v;


    public int getV() {
        return v;
    }

    public void setV(int v) {
        this.v = v;
    }
}
