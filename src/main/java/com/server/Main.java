package com.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.java_websocket.WebSocket;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.json.JSONArray;
import org.json.JSONObject;

public class Main extends WebSocketServer {

    private static final List<String> PLAYER_NAMES = Arrays.asList("A", "B");


    private Map<WebSocket, String> clients;
    private int readyPlayers = 0;
    private List<String> availableNames;
    private Map<String, JSONObject> clientMousePositions = new HashMap<>();

    private static Map<String, JSONObject> selectableObjectsPlayer1 = new HashMap<>();
    private static Map<String, JSONObject> selectableObjectsPlayer2 = new HashMap<>();

    private Map<String, int[]> player1PlacedShips = new HashMap<>();
    private Map<String, int[]> player2PlacedShips = new HashMap<>();

    private boolean  playerATurn = true; // 1 = Player1 && 2 = Player2
    private int timer = 60;

    public Main(InetSocketAddress address) {
        super(address);
        clients = new ConcurrentHashMap<>();
        resetAvailableNames();
    }

    private void resetAvailableNames() {
        availableNames = new ArrayList<>(PLAYER_NAMES);
        Collections.shuffle(availableNames);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String sessionId = handshake.getFieldValue("Session-ID");
        if (sessionId != null && clients.containsValue(sessionId)) {
            System.out.println("Client reconnected with existing Session ID: " + sessionId);
        } else {
            sessionId = UUID.randomUUID().toString();
            System.out.println("New WebSocket client connected with Session ID: " + sessionId);
        }
        
        clients.put(conn, sessionId);
    
        boolean isPlayer1 = (clients.size() == 1);  

        
    
        JSONObject playerInfo = new JSONObject();
        playerInfo.put("type", "playerInfo");
        playerInfo.put("clientId", sessionId);
        playerInfo.put("isPlayer1", isPlayer1);
    
        try {
            conn.send(playerInfo.toString());
        } catch (WebsocketNotConnectedException e) {
            System.out.println("Failed to send playerInfo to client: " + sessionId);
        }
    
        sendClientsList();
        sendCountdown();
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String sessionId = clients.get(conn);
        clients.remove(conn);
        availableNames.add(sessionId);
        System.out.println("WebSocket client with Session ID " + sessionId + " disconnected. Reason: " + reason);
        readyPlayers = 0;
        selectableObjectsPlayer1 = new HashMap<>();
        selectableObjectsPlayer2 = new HashMap<>();
        player1PlacedShips = new HashMap<>();
        player2PlacedShips = new HashMap<>();

        sendClientsList();
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        JSONObject obj = new JSONObject(message);
        
        if (obj.has("type")) {
            String type = obj.getString("type");
            
            switch (type) {
                case "username":
                    String clientID = obj.getString("id");
                    String username = obj.getString("user");

                    // Check if the map contains this client ID and update it with the username
                    for (Map.Entry<WebSocket, String> entry : clients.entrySet()) {
                        if (entry.getValue().equals(clientID)) {
                            clients.put(conn, username); // Replace client ID with username
                            break;
                        }
                    }

                    // Notify all clients about the updated clients list
                    sendClientsList();
                    break;
                case "clientMouseMoving":
                    // Obtenim el clientId del missatge
                    String clientId = obj.getString("clientId");   
                    clientMousePositions.put(clientId, obj);
        
                    // Prepara el missatge de tipus 'serverMouseMoving' amb les posicions de tots els clients
                    JSONObject rst0 = new JSONObject();
                    rst0.put("type", "serverMouseMoving");
                    rst0.put("positions", clientMousePositions);
        
                    // Envia el missatge a tots els clients connectats
                    broadcastMessage(rst0.toString(), null, null);
                    break;
                case "playerReady":
                    JSONObject rivalReady = new JSONObject();
                    rivalReady.put("type", "rivalReady");

                    boolean player1 = obj.getBoolean("player1");
                    if (player1) {
                        rivalReady.put("player1ready", true);
                        setPlacedShips(obj.getJSONObject("placedShips"), "player1");
                    } else {
                        rivalReady.put("player2ready", true);
                        setPlacedShips(obj.getJSONObject("placedShips"), "player2");
                    }
                    readyPlayers += 1;
                    sendGameReady();
                    broadcastMessage(rivalReady.toString(), null, null);

                case "playerShot":
                    handlePlayerShot(obj, conn);
                    break;
                // Servidor
                case "playerWon":
                    String winnerClientId = obj.getString("clientId");
                    boolean isPlayer1 = obj.getBoolean("isPlayer1");

                    JSONObject endGameMessage = new JSONObject();
                    endGameMessage.put("type", "endGame");

                    endGameMessage.put("winner", winnerClientId);
                    
                    broadcastMessage(endGameMessage.toString(), null, null);

                    break;

                    

                    
                    

            }
        }
    }
   
    private void setPlacedShips(JSONObject objects, String string) {
        Map<String, int[]> targetShips;
        if (string.equals("player1")) {
            targetShips = player1PlacedShips;
        } else {
            targetShips = player2PlacedShips;
        }
        targetShips.clear();


        for (String objectId : objects.keySet()) {
            JSONArray positionArray = objects.getJSONArray(objectId);  
            int[] positionObject = new int[4];  
            positionObject[0] = positionArray.getInt(1);
            positionObject[1] = positionArray.getInt(0);
            if (positionArray.getInt(2) > positionArray.getInt(0)) {
                positionObject[2] = positionArray.getInt(2) - positionArray.getInt(0) + 1;
                positionObject[3] = 0;
            } else {
                positionObject[2] = positionArray.getInt(3) - positionArray.getInt(1) + 1;
                positionObject[3] = 1;
            }
            targetShips.put(objectId, positionObject); 
        }
    }

    private void handlePlayerShot(JSONObject obj, WebSocket shooter) {
        if (obj.has("clientId")) {
            String clientId = obj.getString("clientId");
            boolean isPlayer1 = obj.getBoolean("isPlayer1");
            int x = obj.getInt("x");
            int y = obj.getInt("y");
        
            boolean hit = checkHit(x, y, shooter,isPlayer1);
    
            JSONObject shotResult = new JSONObject();
            shotResult.put("type", "shotResult");
            shotResult.put("clientId", clientId);
            shotResult.put("x", x);
            shotResult.put("y", y);
            shotResult.put("result", hit ? "hit" : "miss");

            if (hit) {
                updateTimer();
            }else{
                changeTurn();
            }
    
    
            broadcastMessage(shotResult.toString(), null, null);
        }   
    }
    
    

    private boolean checkHit(int x, int y, WebSocket shooter,boolean isPlayer1) {
        Map<String, int[]> targetShips = isPlayer1 ? player2PlacedShips : player1PlacedShips;
    
        for (Map.Entry<String, int[]> entry : targetShips.entrySet()) {
            int[] position = entry.getValue();
            int startY = position[0];
            int startX = position[1];
            int length = position[2];
            int direction = position[3];
    
            if (direction == 0) { 
                for (int i = 0; i < length; i++) {
                    if (startY == y && (startX + i) == x) {
                        return true; // Hit
                    }
                }
            } else if (direction == 1) {
                for (int i = 0; i < length; i++) {
                    if ((startY + i) == y && startX == x) {
                        return true; // Hit
                    }
                }
            }
        }
        return false; // Miss
    }
    
    

    private void broadcastMessage(String message, WebSocket sender, List<String> targetClients) {
        for (Map.Entry<WebSocket, String> entry : clients.entrySet()) {
            WebSocket conn = entry.getKey();
            String sessionId = entry.getValue();
            if (conn != sender && (targetClients == null || targetClients.contains(sessionId))) {
                try {
                    conn.send(message);
                } catch (WebsocketNotConnectedException e) {
                    System.out.println("Client with Session ID " + sessionId + " not connected.");
                    clients.remove(conn);
                    availableNames.add(sessionId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendPrivateMessage(String destination, String message, WebSocket senderConn) {
        boolean found = false;

        for (Map.Entry<WebSocket, String> entry : clients.entrySet()) {
            if (entry.getValue().equals(destination)) {
                found = true;
                try {
                    entry.getKey().send(message);
                    JSONObject confirmation = new JSONObject();
                    confirmation.put("type", "confirmation");
                    confirmation.put("message", "Message sent to " + destination);
                    senderConn.send(confirmation.toString());
                } catch (WebsocketNotConnectedException e) {
                    System.out.println("Client " + destination + " not connected.");
                    clients.remove(entry.getKey());
                    availableNames.add(destination);
                    notifySenderClientUnavailable(senderConn, destination);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
        }

        if (!found) {
            System.out.println("Client " + destination + " not found.");
            notifySenderClientUnavailable(senderConn, destination);
        }
    }

    private void notifySenderClientUnavailable(WebSocket sender, String sessionId) {
        JSONObject rst = new JSONObject();
        rst.put("type", "error");
        rst.put("message", "Client with Session ID " + sessionId + " is not available.");
    
        try {
            sender.send(rst.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendClientsList() {
        JSONArray clientList = new JSONArray();
        for (String clientName : clients.values()) {
            clientList.put(clientName);
        }

        Iterator<Map.Entry<WebSocket, String>> iterator = clients.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<WebSocket, String> entry = iterator.next();
            WebSocket conn = entry.getKey();
            String clientName = entry.getValue();

            JSONObject rst = new JSONObject();
            rst.put("type", "clients");
            rst.put("id", clientName);
            rst.put("list", clientList);

            try {
                conn.send(rst.toString());
            } catch (WebsocketNotConnectedException e) {
                System.out.println("Client " + clientName + " not connected.");
                iterator.remove();
                availableNames.add(clientName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void sendCountdown() {
        int requiredNumberOfClients = 2;
        if (clients.size() == requiredNumberOfClients) {
            try {
                sendClientsList();
                Thread.sleep(1000);
                for (int i = 5; i >= 0; i--) {
                    JSONObject msg = new JSONObject();
                    msg.put("type", "countdown");
                    msg.put("value", i);
                    broadcastMessage(msg.toString(), null, null);
                    if (i == 0) {
                        sendServerSelectableObjects();
                    } else {
                        try {
                            Thread.sleep(750);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
        }
    }

    public void sendGameReady() {
        if (readyPlayers >= 2) {
            JSONObject gameReady = new JSONObject();
            gameReady.put("type", "gameReady");
            gameReady.put("player1ships", player1PlacedShips);
            gameReady.put("player2ships", player2PlacedShips);
            gameReady.put("currentTurn", playerATurn);
            gameReady.put("timer", timer);
            broadcastMessage(gameReady.toString(), null, null);

            startTimer();
        }
    }

    private void startTimer() {
        new Thread(() -> {
            while (timer > 0) {
                try {
                    Thread.sleep(1000);
                    timer--;
                    JSONObject timerUpdate = new JSONObject();
                    timerUpdate.put("type", "timerUpdate");
                    timerUpdate.put("timer", timer);
                    timerUpdate.put("currentTurn", playerATurn);
                    broadcastMessage(timerUpdate.toString(), null, null);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            changeTurn();
        }).start();
    }

    private void changeTurn() {
        playerATurn = (playerATurn) ? false : true;
        timer = 60;
        
        JSONObject turnChange = new JSONObject();
        turnChange.put("type", "turnChange");
        turnChange.put("currentTurn", playerATurn);
        turnChange.put("timer", timer);
        broadcastMessage(turnChange.toString(), null, null);
        
        startTimer();
    }

    private void updateTimer(){
        JSONObject timerUpdate = new JSONObject();
        timerUpdate.put("type", "timerUpdate");
        timerUpdate.put("timer", 60);
        timerUpdate.put("currentTurn", playerATurn);
        broadcastMessage(timerUpdate.toString(), null, null);
    }

    public void sendServerSelectableObjects() {
        // Prepara el missatge de tipus 'serverObjects' amb les posicions de tots els clients
        JSONObject rst1 = new JSONObject();
        rst1.put("type", "serverSelectableObjects");
        rst1.put("selectableObjectsPlayer1", selectableObjectsPlayer1);
        rst1.put("selectableObjectsPlayer2", selectableObjectsPlayer2);

        // Envia el missatge a tots els clients connectats
        broadcastMessage(rst1.toString(), null, null);
    }
   
    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket server started on port: " + getPort());
        setConnectionLostTimeout(0);
        setConnectionLostTimeout(100);
    }

    public static String askSystemName() {
        StringBuilder resultat = new StringBuilder();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("uname", "-r");
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                resultat.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return "Error: El procés ha finalitzat amb codi " + exitCode;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
        return resultat.toString().trim();
    }

    public static JSONObject shipMaker(JSONObject obj, String objectId, int ogx, int ogy, int x, int y, int cols, int rows, String color) {
        obj.put("objectId", objectId);
        obj.put("ogx", ogx);
        obj.put("ogy", ogy);
        obj.put("x", x);
        obj.put("y", y);
        obj.put("cols", cols);
        obj.put("rows", rows);
        obj.put("color", color);
        return obj;
    }

    public static void main(String[] args) {

        String systemName = askSystemName();

        // WebSockets server
        Main server = new Main(new InetSocketAddress(3000));
        server.start();
        
        LineReader reader = LineReaderBuilder.builder().build();
        System.out.println("Server running. Type 'exit' to gracefully stop it.");

        JSONObject player1Submarine1 = new JSONObject();
        selectableObjectsPlayer1.put("Submarine1", shipMaker(player1Submarine1, "Submarine1", 300, 25, 300, 25, 2, 1, "yellow"));

        JSONObject player1Submarine2 = new JSONObject();
        selectableObjectsPlayer1.put("Submarine2", shipMaker(player1Submarine2, "Submarine2", 400, 175, 300, 175, 1, 2, "yellow"));

        JSONObject player1Cruiser1 = new JSONObject();
        selectableObjectsPlayer1.put("Cruiser1", shipMaker(player1Cruiser1, "Cruiser1", 300, 75, 300, 75, 3, 1, "green"));

        JSONObject player1Cruiser2 = new JSONObject();
        selectableObjectsPlayer1.put("Cruiser2", shipMaker(player1Cruiser2, "Cruiser2", 350, 175, 350, 175, 1, 3, "green"));

        JSONObject player1Battleship = new JSONObject();
        selectableObjectsPlayer1.put("Battleship", shipMaker(player1Battleship, "Battleship", 300, 175, 400, 175, 1, 4, "blue"));

        JSONObject player1Carrier = new JSONObject();
        selectableObjectsPlayer1.put("Carrier", shipMaker(player1Carrier, "Carrier", 300, 125, 300, 125, 5, 1, "red"));

        JSONObject player2Submarine1 = new JSONObject();
        selectableObjectsPlayer2.put("Submarine1", shipMaker(player2Submarine1, "Submarine1", 300, 25, 300, 25, 2, 1, "yellow"));

        JSONObject player2Submarine2 = new JSONObject();
        selectableObjectsPlayer2.put("Submarine2", shipMaker(player2Submarine2, "Submarine2",400, 175, 300, 175, 1, 2, "yellow"));

        JSONObject player2Cruiser1 = new JSONObject();
        selectableObjectsPlayer2.put("Cruiser1", shipMaker(player2Cruiser1, "Cruiser1",300, 75, 300, 75, 3, 1, "green"));

        JSONObject player2Cruiser2 = new JSONObject();
        selectableObjectsPlayer2.put("Cruiser2", shipMaker(player2Cruiser2, "Cruiser2", 350, 175, 350, 175, 1, 3, "green"));

        JSONObject player2Battleship = new JSONObject();
        selectableObjectsPlayer2.put("Battleship", shipMaker(player2Battleship, "Battleship", 300, 175, 400, 175, 1, 4, "blue"));

        JSONObject player2Carrier = new JSONObject();
        selectableObjectsPlayer2.put("Carrier", shipMaker(player2Carrier, "Carrier", 300, 125, 300, 125, 5, 1, "red"));


        try {
            while (true) {
                String line = null;
                try {
                    line = reader.readLine("> ");
                } catch (UserInterruptException e) {
                    continue;
                } catch (EndOfFileException e) {
                    break;
                }

                line = line.trim();

                if (line.equalsIgnoreCase("exit")) {
                    System.out.println("Stopping server...");
                    try {
                        server.stop(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    break;
                } else {
                    System.out.println("Unknown command. Type 'exit' to stop server gracefully.");
                }
            }
        } finally {
            System.out.println("Server stopped.");
        }
    }
}