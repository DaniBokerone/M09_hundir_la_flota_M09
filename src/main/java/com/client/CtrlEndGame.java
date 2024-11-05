package com.client;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class CtrlEndGame implements Initializable {

    @FXML
    private Label gameResult;
    @FXML
    private Label resultText;
    @FXML
    private Button returnButton;

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        
    }

    public void setMessageGame(Boolean isWinner) {
        if (isWinner) {
            gameResult.setText("!!! You have won the game !!!");
            resultText.setText("You should prove your skill by trying to win another game");
        }else{
            gameResult.setText("You lost");
            resultText.setText("Evene the greatest can fail, try again to prove others you're the best");
        }
        
    }

    @FXML
    private void handleReturnButtonClick() {
        if (Main.wsClient != null) {
            Main.wsClient.forceExit();
        }
        
        UtilsViews.setViewAnimating("ViewConfig"); 
    }
    
}
