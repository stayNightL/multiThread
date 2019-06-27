package com.multiThread;

import org.junit.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ConCurrentHashMapTest {
    @Test
    public  void Test1(){
        ConcurrentHashMap concurrentHashMap = new ConcurrentHashMap();
        ConcurrentLinkedDeque<Integer> integers = new ConcurrentLinkedDeque<>();
        integers.pop();
    }
}
