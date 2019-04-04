package com.condition;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BondLink {
        private ReentrantLock lock = new ReentrantLock();
        private Condition  addCondition = lock.newCondition();
        private Condition  removeCondition = lock.newCondition();
        private int[] items;
        private int currentCount = 0;
        public BondLink(int size){
            items = new int[size];
        }
        public  void addItem(int item) throws InterruptedException {
            lock.lock();
            System.out.println("item: "+item);
            if (currentCount==items.length-1){
                System.out.println("addCondition:await");
                addCondition.await();
            }
            currentCount++;
            items[currentCount] = item;
            if (currentCount==1){
                System.out.println("removeCondition:signalAll");
                removeCondition.signalAll();
            }
            lock.unlock();
        }
        public void removeItem() throws InterruptedException {
            lock.lock();
            if (currentCount ==0 ){
                System.out.println("removeCondition:await");
                removeCondition.await();
            }
            items[currentCount] = 0;
            currentCount -- ;
            if (currentCount == items.length-2){
                System.out.println("addCondition:signalAll");
                addCondition.signalAll();
            }
            lock.unlock();
        }

    }
