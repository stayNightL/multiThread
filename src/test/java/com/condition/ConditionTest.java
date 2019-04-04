package com.condition;

import org.junit.Test;

public class ConditionTest {
    @Test
    public void Test1(){
       final BondLink bondLink = new BondLink(20);
        Thread add = new Thread(){
            @Override
            public void run() {
                while(true){
                    try {
                        bondLink.addItem(7);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        add.setName("add");
        add.start();
        while(true){
            try {
                bondLink.removeItem();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }
}
