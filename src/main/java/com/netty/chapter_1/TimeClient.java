package com.netty.chapter_1;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class TimeClient {
    public static void main(String[] args) {
        int port = 8080;
        if (args != null && args.length>0) {
            try {
                port = Integer.valueOf(args[0]);
            } catch (NumberFormatException e) {

            }
        }
        try(Socket socket = new Socket("127.0.0.1",port);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        ){
            while (true) {
                out.println("query system time");
                System.out.println("send 2 server success.");
                String res = in.readLine();
                System.out.println("Now is :"+res);
            }
        }catch (Exception e){

        }
    }
}
