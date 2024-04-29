package com.example.mini_chat_project;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;

class ClientHandler extends Thread {
    //생성자를 통해서 클라이언트 소켓을 얻어옴.
    private Socket socket;
    private String id;
    private Map<String, PrintWriter> chatClients;
    private Map<String, Integer> chatRooms;

    private BufferedReader in;
    PrintWriter out;

    public ClientHandler(Socket socket, Map<String, PrintWriter> chatClients, Map<String, Integer> chatRooms) {
        this.socket = socket;
        this.chatClients = chatClients;
        this.chatRooms = chatRooms;

        //클라이언트가 생성될 때 클라이언트로 부터 아이디를 얻어오게 하고 싶어요.
        //각각 클라이언트와 통신 할 수 있는 통로얻어옴.
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            id = in.readLine();

            System.out.println(id + "님의 IP 주소 : " + socket.getInetAddress().getHostAddress());

            //동시에 일어날 수도..
            synchronized (chatClients) {
                chatClients.put(this.id, out);
                chatRooms.put(this.id, 0);    // 0 : 초기 상태 (로비)
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        System.out.println(id + " 닉네임의 사용자가 연결했습니다.");

        // 클라이언트에게 명령어 안내
        String order = "==========명령어==========" +
                "\n방 목록 보기 : /list" +
                "\n방 생성 : /create" +
                "\n방 입장 : /join [방번호]" +
                "\n방 나가기 : /exit" +
                "\n접속종료 : /bye" +
                "\n==========================";
        out.println(order); // 서버에 명령어를 전송

        //연결된 클라이언트가 메시지를 전송하면, 그 메시지를 받아서 다른 사용자들에게 보내줌..
        String msg = null;
        try {
            while ((msg = in.readLine()) != null) {
                if ("/list".equalsIgnoreCase(msg)) {
                    getRoomList();  // 방 목록
                } else if ("/create".equalsIgnoreCase(msg)) {
                    createRoom();   // 방 만들기
                } else if (msg.indexOf("/join") == 0) {
                    joinRoom(msg);  // 방 접속
                } else if ("/exit".equalsIgnoreCase(msg)) {
                    exitRoom(); // 방 나가기
                } else if ("/bye".equalsIgnoreCase(msg)) {  // 종료
                    System.out.println(id + " 닉네임의 사용자가 연결을 끊었습니다.");
                    break;
                } else if (msg.indexOf("/whisper") == 0){
                    sendMsg(msg);   // 귓속말 기능
                } else {
                    broadcast(id + " : " + msg, chatRooms.get(id));
                }

            }
        } catch (IOException e) {
            System.out.println(e);
            e.printStackTrace();
        } finally {
            synchronized (chatClients) {
                chatClients.remove(id);
            }

            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    // 방 목록 가져오기
    public void getRoomList() {
        boolean roomEmpty = true;
        StringBuilder roomList = new StringBuilder("==========방목록==========\n");

        synchronized (chatRooms) {
            for (Map.Entry<String, Integer> entry : chatRooms.entrySet()) {
                String userId = entry.getKey();
                Integer roomNums = entry.getValue();

                if (roomNums != 0) {  // 초기 설정 0 은 목록에서 제외해야함
                    roomList.append("Room :").append(roomNums).append("\n");
                    roomEmpty = false;
                }
            }

            if(roomEmpty){
                roomList.append("생성된 방이 없습니다.\n");
            }

            roomList.append("==========================");
        }

        out.println(roomList);
    }

    // 방 만들기
    public void createRoom() {
        int newRoomNum = getNextRoomNumber();
        synchronized (chatRooms) {
            chatRooms.put(id, newRoomNum);
            out.println("방 번호 " + newRoomNum + " 이(가) 생성되었습니다.");
            out.println("Room " + newRoomNum + "에 입장했습니다.");
        }
    }

    // 방 나가기
    public void exitRoom() {
        int currentRoom = chatRooms.get(id);
        synchronized (chatRooms) {
            chatRooms.put(id, 0); // 다시 로비로 돌아감
            out.println("Room " + currentRoom + "에서 퇴장하여 로비로 이동합니다.");
        }
        broadcast(id + "님이 방을 나갔습니다.", currentRoom);

        //TODO : 이 메세지 초기메뉴랑 중복되니까 따로 빼서 구현해야할까..?
        String order = "==========명령어==========" +
                "\n방 목록 보기 : /list" +
                "\n방 생성 : /create" +
                "\n방 입장 : /join [방번호]" +
                "\n방 나가기 : /exit" +
                "\n접속종료 : /bye" +
                "\n==========================";
        out.println(order); // 서버에 명령어를 전송

        // 방에 아무도 없을 경우 방 삭제
        if (!chatRooms.containsValue(0)) {
            removeRoom();
        }
    }

    // 같은 방 사용자에게 알려주는 메서드
    public void broadcast(String msg,int roomNum) {
        synchronized (chatClients) {
            for (Map.Entry<String, Integer> entry : chatRooms.entrySet()) {
                String userId = entry.getKey();
                Integer userRoomNumber = entry.getValue();

                if (userRoomNumber != null && userRoomNumber.equals(roomNum)) {
                    PrintWriter userOut = chatClients.get(userId);
                    if (userOut != null) {
                        userOut.println(msg);
                    }
                }
            }
        }
    }

    // 귓속말 기능
    public void sendMsg(String msg) {
        int frisrtSpaceIndex = msg.indexOf(" ");
        if (frisrtSpaceIndex == -1) return; // 공백이 없다면, 실행 안함

        int secondSpaceIndex = msg.indexOf(" ", frisrtSpaceIndex + 1);
        if (secondSpaceIndex == -1) return;  // 수신자는 있으나 보낼 메세지가 없는것

        String to = msg.substring(frisrtSpaceIndex + 1, secondSpaceIndex);
        String message = msg.substring(secondSpaceIndex + 1);
        // 나한테 보이는 message
        out.println(to + "님에게 보낸 귓속말 :" + message);

        // to(수신자)에게 message 를 전송
        PrintWriter pw = chatClients.get(to);
        if (pw != null) {
            pw.println(id + "님이 보낸 귓속말 : " + message);
        } else {
            System.out.println("오류 : 수신자 " + to + "님을 찾을 수 없습니다.");
        }
    }


    // 방 만들기
    public int getNextRoomNumber() {
        int maxRoomNumber = 0;
        // 현재 존재하는 방 중 가장 큰 번호를 찾음
        for (int roomNumber : chatRooms.values()) {
            if (roomNumber > maxRoomNumber) {
                maxRoomNumber = roomNumber;
            }
        }

        // 다음 방 번호는 가장 큰 번호 + 1
        return maxRoomNumber + 1;
    }

    // 방 입장
    public void joinRoom(String msg) {
        int frisrtSpaceIndex = msg.indexOf(" ");
        if (frisrtSpaceIndex == -1) {
            out.println("방 번호를 입력하세요. 예: /join [방번호]");
            return;
        }

        int joinRoom = Integer.parseInt(msg.substring(frisrtSpaceIndex + 1).trim());

        if(!chatRooms.containsValue(joinRoom)){
            out.println("해당 방번호는 존재하지 않습니다. 방 목록을 확인해주세요.");
            return;
        }

        try {
            synchronized (chatRooms) {
                chatRooms.put(id, joinRoom);
                out.println("Room " + joinRoom + "에 입장했습니다.");
            }
            // join 후 브로드캐스트
            broadcast(id + "님이 방에 입장하셨습니다.", joinRoom);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 방 삭제
    public void removeRoom() {
        synchronized (chatRooms) {
            for (Map.Entry<String, Integer> entry : chatRooms.entrySet()) {
                if (entry.getValue() != 0) {
                    return; // 다른 방에 있으면 삭제하지 않음
                }
            }

            int roomDel = chatRooms.values().iterator().next();
            chatRooms.remove(id);
            out.println(roomDel + "번 방이 삭제되었습니다.");
        }
    }
}
