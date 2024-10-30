package com.client;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import org.json.JSONObject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

public class CtrlPlay implements Initializable {

    @FXML
    private Button ready;

    @FXML
    private Label rivalReady;

    @FXML
    private Canvas canvas;
    private GraphicsContext gc;
    private Boolean showFPS = false;

    private PlayTimer animationTimer;
    private PlayGrid grid;
    
    private Boolean playerReady = false;
    private Boolean enemyReady = false;
    private Boolean mouseDragging = false;
    private double mouseOffsetX, mouseOffsetY;

    private Map<String, JSONObject> player1Ships = new HashMap<>();
    private Map<String, JSONObject> player2Ships = new HashMap<>();
    private String selectedObject = "";

    private Map<String, int[]> player1PlacedShips = new HashMap<>();
    private Map<String, int[]> player2PlacedShips = new HashMap<>();

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        // Get drawing context
        this.gc = canvas.getGraphicsContext2D();
        ready.setDisable(true);
        rivalReady.setVisible(false);

        // Set listeners
        UtilsViews.parentContainer.heightProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
        UtilsViews.parentContainer.widthProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
        
        canvas.setOnMousePressed(this::onMousePressed);
        canvas.setOnMouseDragged(this::onMouseDragged);
        canvas.setOnMouseReleased(this::onMouseReleased);

        // Define grid
        grid = new PlayGrid(25, 25, 25, 10, 10);

        // Start run/draw timer bucle
        animationTimer = new PlayTimer(this::run, this::draw, 0);
        start();
    }

    // When window changes its size
    public void onSizeChanged() {

        double width = UtilsViews.parentContainer.getWidth();
        double height = UtilsViews.parentContainer.getHeight();
        canvas.setWidth(width);
        canvas.setHeight(height);
    }

    // Start animation timer
    public void start() {
        animationTimer.start();
    }

    // Stop animation timer
    public void stop() {
        animationTimer.stop();
    }

    private void onMousePressed(MouseEvent event) {
        if (!playerReady) {
            double mouseX = event.getX();
            double mouseY = event.getY();

            selectedObject = "";
            mouseDragging = false;

            for (String objectId : (Main.isPlayer1 ? player1Ships : player2Ships).keySet()) {
                JSONObject obj = (Main.isPlayer1 ? player1Ships : player2Ships).get(objectId);
                int objX = obj.getInt("x");
                int objY = obj.getInt("y");
                int cols = obj.getInt("cols");
                int rows = obj.getInt("rows");

                if (isPositionInsideObject(mouseX, mouseY, objX, objY,  cols, rows)) {
                    selectedObject = objectId;
                    mouseDragging = true;
                    mouseOffsetX = event.getX() - objX;
                    mouseOffsetY = event.getY() - objY;
                    break;
                }
            }
        }
    }

    private void onMouseDragged(MouseEvent event) {
        if (mouseDragging) {
            JSONObject obj = (Main.isPlayer1 ? player1Ships : player2Ships).get(selectedObject);
            double objX = event.getX() - mouseOffsetX;
            double objY = event.getY() - mouseOffsetY;
            double objXend = (event.getX() + (grid.getCellSize() * (obj.getInt("cols") - 1))) - mouseOffsetX;
            double objYend = (event.getY() + (grid.getCellSize() * (obj.getInt("rows") - 1))) - mouseOffsetY;
            
            obj.put("x", objX);
            obj.put("y", objY);
            obj.put("col", grid.getCol(objX));
            obj.put("row", grid.getRow(objY));
            obj.put("colend", grid.getCol(objXend));
            obj.put("rowend", grid.getRow(objYend));
        }
    }

    private void onMouseReleased(MouseEvent event) {
        if (selectedObject != "") {
            JSONObject obj = (Main.isPlayer1 ? player1Ships : player2Ships).get(selectedObject);
            int objCol = obj.getInt("col");
            int objRow = obj.getInt("row");
            int objColEnd = obj.getInt("colend");
            int objRowEnd = obj.getInt("rowend");

            if ((Main.isPlayer1 ? player1PlacedShips : player2PlacedShips).containsKey(obj.getString("objectId"))) {
                (Main.isPlayer1 ? player1PlacedShips : player2PlacedShips).remove(obj.getString("objectId"));
                ready.setDisable(true);
            }

            if (objCol != -1 && objRow != -1 && objColEnd != -1 && objRowEnd != -1) {
                if ((Main.isPlayer1 ? player1PlacedShips : player2PlacedShips).isEmpty()) {
                    obj.put("x", grid.getCellX(objCol));
                    obj.put("y", grid.getCellY(objRow));
                    (Main.isPlayer1 ? player1PlacedShips : player2PlacedShips).put(obj.getString("objectId"), new int[]{obj.getInt("col"), obj.getInt("row"), obj.getInt("colend"), obj.getInt("rowend")});
                } else {
                    if (checkOverlapping(obj)) {
                        obj.put("x", obj.getInt("ogx"));
                        obj.put("y", obj.getInt("ogy"));
                    } else {
                        obj.put("x", grid.getCellX(objCol));
                        obj.put("y", grid.getCellY(objRow));
                        (Main.isPlayer1 ? player1PlacedShips : player2PlacedShips).put(obj.getString("objectId"), new int[]{obj.getInt("col"), obj.getInt("row"), obj.getInt("colend"), obj.getInt("rowend")});
                    }
                }

            } else {
                obj.put("x", obj.getInt("ogx"));
                obj.put("y", obj.getInt("ogy"));
            }

            System.out.println((Main.isPlayer1 ? player1PlacedShips : player2PlacedShips));
            if (Main.isPlayer1 ? player1PlacedShips.size() == 6 : player2PlacedShips.size() == 6) {
                ready.setDisable(false);
            }

            mouseDragging = false;
            selectedObject = "";
        }
    }

    public boolean checkOverlapping(JSONObject placingShip) {
        if (placingShip.getInt("colend") - placingShip.getInt("col") > placingShip.getInt("rowend") - placingShip.getInt("row")) {
            for (Map.Entry<String, int[]> placedShip : (Main.isPlayer1 ? player1PlacedShips : player2PlacedShips).entrySet()) {
                if (placedShip.getValue()[2] - placedShip.getValue()[0] > placedShip.getValue()[3] - placedShip.getValue()[1]) {
                    for (int i = 0; i < placedShip.getValue()[2] - placedShip.getValue()[0] + 1; i++) {
                        for (int j = 0; j < placingShip.getInt("colend") - placingShip.getInt("col") + 1; j++) {
                            if (placedShip.getValue()[0] + i == placingShip.getInt("col") + j && placedShip.getValue()[1] == placingShip.getInt("row")) {
                                return true;
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < placedShip.getValue()[3] - placedShip.getValue()[1] + 1; i++) {
                        for (int j = 0; j < placingShip.getInt("colend") - placingShip.getInt("col") + 1; j++) {
                            if (placedShip.getValue()[0] == placingShip.getInt("col") + j && placedShip.getValue()[1] + i == placingShip.getInt("row")) {
                                return true;
                            } 
                        }
                    }
                }
            }
        } else {
            for (Map.Entry<String, int[]> placedShip : (Main.isPlayer1 ? player1PlacedShips : player2PlacedShips).entrySet()) {
                if (placedShip.getValue()[2] - placedShip.getValue()[0] > placedShip.getValue()[3] - placedShip.getValue()[1]) {
                    for (int i = 0; i < placedShip.getValue()[2] - placedShip.getValue()[0] + 1; i++) {
                        for (int j = 0; j < placingShip.getInt("rowend") - placingShip.getInt("row") + 1; j++) {
                            if (placedShip.getValue()[0] + i == placingShip.getInt("col") && placedShip.getValue()[1] == placingShip.getInt("row") + j) {
                                return true;
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < placedShip.getValue()[3] - placedShip.getValue()[1] + 1; i++) {
                        for (int j = 0; j < placingShip.getInt("rowend") - placingShip.getInt("row") + 1; j++) {
                            if (placedShip.getValue()[0] == placingShip.getInt("col") && placedShip.getValue()[1] + i == placingShip.getInt("row") + j) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        
        
        return false;
    }



    public void setSelectableObjects(JSONObject objects, String isPlayer) {
        Map<String, JSONObject> targetShips;
        if (isPlayer.equals("player1")) {
            targetShips = player1Ships;
        } else {
            targetShips = player2Ships;
        }
        targetShips.clear();
        for (String objectId : objects.keySet()) {
            JSONObject positionObject = objects.getJSONObject(objectId);
            targetShips.put(objectId, positionObject);
        }
    }

    public Boolean isPositionInsideObject(double positionX, double positionY, int objX, int objY, int cols, int rows) {
        double cellSize = grid.getCellSize();
        double objectWidth = cols * cellSize;
        double objectHeight = rows * cellSize;

        double objectLeftX = objX;
        double objectRightX = objX + objectWidth;
        double objectTopY = objY;
        double objectBottomY = objY + objectHeight;

        return positionX >= objectLeftX && positionX < objectRightX &&
               positionY >= objectTopY && positionY < objectBottomY;
    }

    @FXML
    private void onReady(ActionEvent event) {
        playerReady = true;
        ready.setText("Waiting for other player...");
        ready.setStyle("-fx-background-color: #EBEBE4");
        
        JSONObject msgObj = new JSONObject();
        msgObj.put("type", "playerReady");
        msgObj.put("player1", Main.isPlayer1);
        msgObj.put("placedShips", Main.isPlayer1 ? player1PlacedShips : player2PlacedShips);
        if (Main.wsClient != null) {
            Main.wsClient.safeSend(msgObj.toString());
        }
    }

    public void onRivalReady() {
        rivalReady.setVisible(true);
        enemyReady = true;
    }

    public void gameReady() {
        rivalReady.setVisible(false);
        ready.setText("Game starting...");
    }

    // Run game (and animations)
    private void run(double fps) {

        if (animationTimer.fps < 1) { return; }

        // Update objects and animations here
    }

    // Draw game to canvas
    public void draw() {
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        drawGrid();
    
        Map<String, JSONObject> currentPlayerShips = Main.isPlayer1 ? player1Ships : player2Ships;
        for (String objectId : currentPlayerShips.keySet()) {
            JSONObject selectableObject = currentPlayerShips.get(objectId);
            drawSelectableObject(objectId, selectableObject);
        }
    
        if (showFPS) {
            animationTimer.drawFPS(gc);
        }
    }

    public void drawGrid() {
        gc.setStroke(Color.BLACK);

        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                double cellSize = grid.getCellSize();
                double x = grid.getStartX() + col * cellSize;
                double y = grid.getStartY() + row * cellSize;
                gc.strokeRect(x, y, cellSize, cellSize);
            }
        }
    }

    public void drawSelectableObject(String objectId, JSONObject obj) {
        double cellSize = grid.getCellSize();

        int x = obj.getInt("x");
        int y = obj.getInt("y");
        double width = obj.getInt("cols") * cellSize;
        double height = obj.getInt("rows") * cellSize;

        // Seleccionar un color basat en l'objectId
        Color color;
        switch (obj.getString("color")) {
            case "red":
                color = Color.RED;
                break;
            case "blue":
                color = Color.BLUE;
                break;
            case "green":
                color = Color.GREEN;
                break;
            case "yellow":
                color = Color.YELLOW;
                break;
            default:
                color = Color.GRAY;
                break;
        }

        // Dibuixar el rectangle
        gc.setFill(color);
        gc.fillRect(x, y, width, height);

        // Dibuixar el contorn
        gc.setStroke(Color.BLACK);
        gc.strokeRect(x, y, width, height);
    }
}
