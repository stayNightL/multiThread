package com.netty.chapter_1;

import java.io.*;
import java.net.Socket;
import java.util.Date;

public class TimeServerHandler implements Runnable {
    private  Socket socket;

    public TimeServerHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        BufferedReader in =null;
        PrintWriter out = null;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(),true);
            while (true){
                String body = in.readLine();
                if (body ==null) {
                    break;
                }
                System.out.println("Time server receive order: "+body);
                String time = "query system time".equalsIgnoreCase(body) ? new Date().toString() : "bad query";
                out.println(time);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e1) {

                    in =null;
                }
            }
            if (out != null) {
                out.close();
            }
        }
    }
}
