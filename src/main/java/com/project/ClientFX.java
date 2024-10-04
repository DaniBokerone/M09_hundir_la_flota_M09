package com.project;
import org.json.JSONObject;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class ClientFX extends Application {

    public static UtilsWS wsClient;

    public static int port = 3000;
    public static String protocol = "http";
    public static String host = "localhost";
    public static String protocolWS = "ws";

    public static void main(String[] args) {

        // Iniciar app JavaFX   
        launch(args);
    }
    
    @Override
    public void start(Stage stage) throws Exception {

        final int windowWidth = 400;
        final int windowHeight = 300;

        UtilsViews.parentContainer.setStyle("-fx-font: 14 arial;");
        UtilsViews.addView(getClass(), "ViewSockets", "/assets/viewSockets.fxml");

        Scene scene = new Scene(UtilsViews.parentContainer);
        
        stage.setScene(scene);
        stage.onCloseRequestProperty(); // Call close method when closing window
        stage.setTitle("JavaFX - NodeJS");
        stage.setMinWidth(windowWidth);
        stage.setMinHeight(windowHeight);
        stage.show();

        // Add icon only if not Mac
        if (!System.getProperty("os.name").contains("Mac")) {
            Image icon = new Image("file:/icons/icon.png");
            stage.getIcons().add(icon);
        }

        // Iniciar WebSockets
        wsClient = UtilsWS.getSharedInstance(protocolWS + "://" + host + ":" + port);
        wsClient.onMessage((response) -> {
            
            // JavaFX necessita que els canvis es facin des de el thread principal
            Platform.runLater(()->{ 
                // Fer aquí els canvis a la interficie
                JSONObject msgObj = new JSONObject(response);
                CtrlSockets ctrl = (CtrlSockets) UtilsViews.getController("ViewSockets");
                ctrl.receiveMessage(msgObj);
            });
        });
    }

    @Override
    public void stop() { 
        wsClient.forceExit();
        System.exit(1); // Kill all executor services
    }
}
