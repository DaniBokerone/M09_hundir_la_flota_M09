package com.project;

import org.json.JSONObject;
import org.json.JSONArray;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.EndOfFileException;

import java.util.ArrayList;
import java.util.List;

public class ClientCMD {

    private List<String> clientsList;
    private String clientId;
    private UtilsWS wsClient;

    public ClientCMD(String serverUri) {
        clientsList = new ArrayList<>();
        wsClient = UtilsWS.getSharedInstance(serverUri);
        setupwsClient();
    }

    private void setupwsClient() {
        wsClient.onMessage(this::handleMessage);
    }

    private void handleMessage(String message) {
        JSONObject msgObj = new JSONObject(message);
        String type = msgObj.getString("type");

        switch (type) {
            case "clients":
                handleClientsMessage(msgObj);
                break;
            case "bounce":
                System.out.println("\nBounce: " + msgObj.getString("message"));
                break;
            case "broadcast":
                System.out.println("\nBroadcast: " + msgObj.getString("message"));
                System.out.println("(from: " + msgObj.getString("origin") + ")");
                break;
            case "private":
                System.out.println("\nPrivate: " + msgObj.getString("message"));
                System.out.println("(from: " + msgObj.getString("origin") + ")");
                break;
            case "confirmation":
                System.out.println("\n[Confirmation]: " + msgObj.getString("message"));
                break;
            case "error":
                System.out.println("\n[Error]: " + msgObj.getString("message"));
                break;
        }

        System.out.print("> ");
    }

    private void handleClientsMessage(JSONObject msgObj) {
        JSONArray JSONlist = msgObj.getJSONArray("list");
        clientId = msgObj.getString("id");

        clientsList.clear();
        for (int i = 0; i < JSONlist.length(); i++) {
            String value = JSONlist.getString(i);
            if (!value.equals(clientId)) {
                clientsList.add(value);
            }
        }
    }

    public void showHelp() {
        System.out.println("\nAvailable commands (press ↩️ after each command):");
        System.out.println("- list : lists the connected clients");
        System.out.println("- private clientX message↩️ : sends a private message to the client with id X");
        System.out.println("- broadcast message↩️ : sends a message to all clients");
        System.out.println("- bounce message↩️ : sends a message that bounces back to you");
        System.out.println("- myname : shows your client name");
        System.out.println("- exit↩️ : exits the client");
    }

    public void listClients() {
        if (clientsList.isEmpty()) {
            System.out.println("No other clients connected.");
        } else {
            System.out.println("Connected clients:");
            for (String client : clientsList) {
                System.out.println(client);
            }
        }
    }

    public void run() {
        System.out.println("Welcome");
        showHelp();

        LineReader reader = LineReaderBuilder.builder().build();

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

                if (line.isEmpty()) {
                    continue;
                } else if (line.equalsIgnoreCase("help")) {
                    showHelp();
                } else if (line.equalsIgnoreCase("list")) {
                    listClients();
                } else if (line.toLowerCase().startsWith("private")) {
                    handleSendPrivate(line);
                } else if (line.toLowerCase().startsWith("broadcast")) {
                    handleSendBroadcast(line);
                } else if (line.toLowerCase().startsWith("bounce")) {
                    handleSendBounce(line);
                } else if (line.equalsIgnoreCase("myname")) {
                    System.out.println("Your client name is: " + clientId);
                } else if (line.equalsIgnoreCase("exit")) {
                    System.out.println("Exiting client.");
                    wsClient.forceExit();
                    break;
                } else {
                    System.out.println("Unknown command. Type 'help' to see available commands.");
                }
            }
        } finally {
            System.out.println("Finished");
        }
    }

    private void handleSendPrivate(String line) {
        String[] parts = line.split(" ", 3);
        if (parts.length < 3) {
            System.out.println("Usage: private clientX message");
            return;
        }
        String destination = parts[1];
        String message = parts[2];

        JSONObject obj = new JSONObject();
        obj.put("type", "private");
        obj.put("destination", destination);
        obj.put("message", message);
        wsClient.safeSend(obj.toString());
    }

    private void handleSendBroadcast(String line) {
        String[] parts = line.split(" ", 2);
        if (parts.length < 2) {
            System.out.println("Usage: broadcast message");
            return;
        }
        String message = parts[1];

        JSONObject obj = new JSONObject();
        obj.put("type", "broadcast");
        obj.put("message", message);
        wsClient.safeSend(obj.toString());
    }

    private void handleSendBounce(String line) {
        String[] parts = line.split(" ", 2);
        if (parts.length < 2) {
            System.out.println("Usage: bounce message");
            return;
        }
        String message = parts[1];

        JSONObject obj = new JSONObject();
        obj.put("type", "bounce");
        obj.put("message", message);
        wsClient.safeSend(obj.toString());
    }

    public static void main(String[] args) {
        String serverURI = "ws://localhost:3000";
        ClientCMD client = new ClientCMD(serverURI);
        client.run();
    }
}