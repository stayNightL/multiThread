package com.ThreadPool;


import java.io.IOException;

import org.junit.Test;

public class ThreadPoolTest {
    @Test
    public void poolTest() {
        int max = 100;
        DefaultThreadPool<PoolTest> pool = new DefaultThreadPool<>();
        for (int i = 0; i < max; i++) {
            pool.execute(new PoolTest(i));
        }
        try {
            //在这个地方hold，防止测试线程结束
            System.in.read();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    @Test
    public void poolTestTest() {
        new PoolTest(1).run();
    }
    public static void main(String[] args) {
        new PoolTest(1).run();
    }
}
