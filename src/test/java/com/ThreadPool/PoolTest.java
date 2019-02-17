package com.ThreadPool;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class PoolTest implements Runnable {
 //   private static final org.apache.logging.log4j.Logger LOGGER = LogManager.getLogger(Config.class);
 private final Logger logger = LogManager.getLogger(Config.class);

    PoolTest(int i) {
        this.i = i;
    }

    private int i;

    @Override
    public void run() {
        System.out.println();
        try {
         logger.info(Thread.currentThread().getName() + "当前执行的任务：" + i);
        } catch (Exception e) {
            e.printStackTrace();
        }
}
}
