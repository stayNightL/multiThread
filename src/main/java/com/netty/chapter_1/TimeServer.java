package com.netty.chapter_1;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TimeServer {
    public static void main(String[] args) {
        int port = 8080;
        if (args != null && args.length>0) {
            try {
                port = Integer.valueOf(args[0]);
            } catch (NumberFormatException e) {

            }
        }
        ServerSocket server = null;
        try {
            server = new ServerSocket(port);
            System.out.println("blind port: "+port);
            Socket socket = null;
            while (true){
                socket = server.accept();
                new Thread(new TimeServerHandler(socket)).start();
            }
        } catch (IOException e) {


        } finally {
            if (server!=null) {
                try {
                    server.close();
                } catch (IOException e) {
                    server =null;
                }
                server =null;
            }
        }
    }
}
