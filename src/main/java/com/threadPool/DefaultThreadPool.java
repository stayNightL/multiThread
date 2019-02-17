package com.ThreadPool;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultThreadPool<Job extends Runnable>  implements ThreadPoolImp<Job>
{   // 线程池最大限制
    private static final int MAX_WORKER_NUMBERS = 10;
    private static final int DEFAULT_WORKER_NUMBERS = 5;
    private static final int MIN_WORKER_NUMBERS = 1;
    private final LinkedList<Job> jobs = new LinkedList<Job>();
    private final List<Worker> workers = Collections.synchronizedList(new ArrayList<Worker>());
    private int workerNum = DEFAULT_WORKER_NUMBERS;
    private AtomicLong threadNum = new AtomicLong();

    public DefaultThreadPool() {
        initWorkers(DEFAULT_WORKER_NUMBERS);
    }

    public DefaultThreadPool(int num) {
        workerNum = num > MAX_WORKER_NUMBERS ? MAX_WORKER_NUMBERS : num < MIN_WORKER_NUMBERS ? MIN_WORKER_NUMBERS : num;
        initWorkers(workerNum);
    }



    public void initWorkers(int num) {
        for (int i = 0; i < num; i++) {
            Worker worker = new Worker();
            workers.add(worker);
            Thread thread = new Thread(worker, "ThreadPool-worker-" + threadNum.incrementAndGet());
            thread.start();
        }
    }

    class Worker implements Runnable {
        private volatile boolean running = true;

        public void run() {
            while (running) {
                Job job = null;
                synchronized (jobs) {
                    while (jobs.isEmpty()) {
                        try {
                            jobs.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            e.printStackTrace();
                            return;
                        }
                    }
                    job = jobs.removeFirst();
                }

                if (job != null) {
                    job.run();
                }
            }

        }

        public void shutDown() {
            this.running = false;
        }
    }

    @Override
    public void execute(Job job) {
        synchronized (jobs) {
            if (job != null) {
                jobs.addLast(job);
                jobs.notify();
            }
        }
    }

    @Override
    public void shutDown() {
        for (Worker w : workers) {
            w.shutDown();
        }
    }

    @Override
    public void addWorkers(int num) {

    }

    @Override
    public void removeWorkers(int num) {

    }

    @Override
    public int getJobSize() {
        return jobs.size();
    }

}
