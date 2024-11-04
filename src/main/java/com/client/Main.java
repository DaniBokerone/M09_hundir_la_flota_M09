package com.client;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Main extends Application {

    public static UtilsWS wsClient;

    public static boolean isPlayer1 = false;
    public static boolean enemyReady = false;

    public static String clientId = "";
    public static CtrlConfig ctrlConfig;
    public static CtrlWait ctrlWait;
    public static CtrlPlay ctrlPlay;
    public static CtrlGame ctrlGame;

    public static void main(String[] args) {

        // Iniciar app JavaFX   
        launch(args);
    }
    
    @Override
    public void start(Stage stage) throws Exception {

        final int windowWidth = 800;
        final int windowHeight = 400;

        UtilsViews.parentContainer.setStyle("-fx-font: 14 arial;");
        UtilsViews.addView(getClass(), "ViewConfig", "/assets/viewConfig.fxml"); 
        UtilsViews.addView(getClass(), "ViewWait", "/assets/viewWait.fxml");
        UtilsViews.addView(getClass(), "ViewPlay", "/assets/viewPlay.fxml");
        UtilsViews.addView(getClass(), "ViewGame", "/assets/viewGame.fxml");

        ctrlConfig = (CtrlConfig) UtilsViews.getController("ViewConfig");
        ctrlWait = (CtrlWait) UtilsViews.getController("ViewWait");
        ctrlPlay = (CtrlPlay) UtilsViews.getController("ViewPlay");
        ctrlGame = (CtrlGame) UtilsViews.getController("ViewGame");

        Scene scene = new Scene(UtilsViews.parentContainer);
        
        stage.setScene(scene);
        stage.onCloseRequestProperty(); // Call close method when closing window
        stage.setTitle("JavaFX");
        stage.setMinWidth(windowWidth);
        stage.setMinHeight(windowHeight);
        stage.show();

        // Add icon only if not Mac
        if (!System.getProperty("os.name").contains("Mac")) {
            Image icon = new Image("file:/icons/icon.png");
            stage.getIcons().add(icon);
        }
    }

    @Override
    public void stop() { 
        if (wsClient != null) {
            wsClient.forceExit();
        }
        System.exit(1); // Kill all executor services
    }

    public static void pauseDuring(long milliseconds, Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(milliseconds));
        pause.setOnFinished(event -> Platform.runLater(action));
        pause.play();
    }

    public static <T> List<T> jsonArrayToList(JSONArray array, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            T value = clazz.cast(array.get(i));
            list.add(value);
        }
        return list;
    }

    public static void connectToServer() {

        ctrlConfig.txtMessage.setTextFill(Color.BLACK);
        ctrlConfig.txtMessage.setText("Connecting ...");
    
        pauseDuring(1500, () -> { // Give time to show connecting message ...

            String protocol = ctrlConfig.txtProtocol.getText();
            String host = ctrlConfig.txtHost.getText();
            String port = ctrlConfig.txtPort.getText();
            wsClient = UtilsWS.getSharedInstance(protocol + "://" + host + ":" + port);
    
            wsClient.onMessage((response) -> { Platform.runLater(() -> { wsMessage(response); }); });
            wsClient.onError((response) -> { Platform.runLater(() -> { wsError(response); }); });
        });
    }
   
    private static void wsMessage(String response) {
        JSONObject msgObj = new JSONObject(response);
        switch (msgObj.getString("type")) {
            case "playerInfo":
                clientId = msgObj.getString("clientId");
                isPlayer1 = msgObj.getBoolean("isPlayer1");
                System.out.println("Player assigned: " + (isPlayer1 ? "Player 1" : "Player 2"));
                break;
            case "clients":
                if (clientId.isEmpty()) {
                    clientId = msgObj.getString("id");
                }
                if (!"ViewWait".equals(UtilsViews.getActiveView())) {
                    UtilsViews.setViewAnimating("ViewWait");
                }
                List<String> stringList = jsonArrayToList(msgObj.getJSONArray("list"), String.class);
                if (stringList.size() > 0) {
                    ctrlWait.txtPlayer0.setText(stringList.get(0));
                }
                if (stringList.size() > 1) {
                    ctrlWait.txtPlayer1.setText(stringList.get(1));
                }
                break;
            
            case "countdown":
                int value = msgObj.getInt("value");
                String txt = String.valueOf(value);
                if (value == 0) {
                    UtilsViews.setViewAnimating("ViewPlay");
                    //Descomentar para ver vista GAME y comentar linea superior
                    //UtilsViews.setViewAnimating("ViewGame");
                    txt = "GO";
                }
                ctrlWait.txtTitle.setText(txt);
                break;
            case "serverMouseMoving":
                //Descomentar para ver vista GAME y comentar linea superior
                ctrlGame.setPlayersMousePositions(msgObj.getJSONObject("positions"));
                break;
            case "serverSelectableObjects":
                ctrlPlay.setSelectableObjects(msgObj.getJSONObject("selectableObjectsPlayer1"), "player1");
                ctrlPlay.setSelectableObjects(msgObj.getJSONObject("selectableObjectsPlayer2"), "player2");
                break;
            case "rivalReady":
                enemyReady = isPlayer1 ? msgObj.getBoolean("player2ready") : msgObj.getBoolean("player1ready");
                ctrlPlay.onRivalReady();
                break;
            case "gameReady":
                ctrlPlay.gameReady();
                ctrlGame.setupShips(msgObj.getJSONObject("player1ships"), "player1");
                ctrlGame.setupShips(msgObj.getJSONObject("player2ships"), "player2");
                ctrlGame.setupTimer(msgObj.getInt("timer"),msgObj.getBoolean("currentTurn"));
                UtilsViews.setViewAnimating("ViewGame");
                break;
            case "shotResult":
                ctrlGame.updateBoardWithShotResult(msgObj);
                break;
            case "turnChange":
            System.out.println(msgObj.toString());
                boolean isPlayerATurn = msgObj.getBoolean("currentTurn");
                int restartTime = msgObj.getInt("timer");
                ctrlGame.onTurnChange(isPlayerATurn,restartTime);
                break;
            case "timerUpdate":
            System.out.println(msgObj.toString());
                int remainingTime = msgObj.getInt("timer");
                ctrlGame.updateTimer(remainingTime);
                break;
        }
    }

    private static void wsError(String response) {

        String connectionRefused = "Connection refused";
        if (response.indexOf(connectionRefused) != -1) {
            ctrlConfig.txtMessage.setTextFill(Color.RED);
            ctrlConfig.txtMessage.setText(connectionRefused);
            pauseDuring(1500, () -> {
                ctrlConfig.txtMessage.setText("");
            });
        }
    }
}
