package com.example.mini_chat_project;

import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class ChatServer {
    public static void main(String[] args) {
        // 1. 서버 소켓을 생성한다.
        try {
            ServerSocket serverSocket = new ServerSocket(12345);
            System.out.println("서버가 준비되었습니다.");
            // 여러명의 클라이언트의 정보를 기억할 공간
            Map<String, PrintWriter> chatClients = new HashMap<>();
            Map<String, Integer> roomNum = new HashMap<>();

            while(true){
                // 2. accept() 를 통해서 소켓을 얻어온다.
                Socket socket = serverSocket.accept();
                // 여러명의 클라이언트의 정보를 기억할 공간
                new ClientHandler(socket, chatClients, roomNum).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

