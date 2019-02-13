package com.multiThread;

public class ThreadState {
    public static void main(String[] args) {
        new Thread(new TimeWaiting(), "TimeWaitingThread").start();
        new Thread(new Waiting(), "WaitingThread").start(); // 使用 两个 Blocked 线程， 一个 获取 锁 成功， 另一个 被 阻塞
        new Thread(new Blocked(), "BlockedThread- 1").start();
        new Thread(new Blocked(), "BlockedThread- 2").start();
    }

    public static class TimeWaiting implements Runnable {

        @Override
        public void run() {
            while (true) {
                SleepUtil.second(10);
            }
        }
    }

    static class Waiting implements Runnable {
        @Override
        public void run() {
            while (true) {
                synchronized (Waiting.class) {
                    try {
                        Waiting.class.wait();
                    } catch (InterruptedException e) {
                    
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    static class Blocked implements Runnable{
        @Override
        public void run() {
            synchronized(Blocked.class){
            while(true){
                SleepUtil.second(10);
            }
        }
    }
    }

}
