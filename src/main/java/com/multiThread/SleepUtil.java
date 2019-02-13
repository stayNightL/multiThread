package com.multiThread;

import java.util.concurrent.TimeUnit;

public class SleepUtil {
    public static void second(long seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            
            e.printStackTrace();
        }
    }
}