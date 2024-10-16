package com.client;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import org.json.JSONObject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

public class CtrlGame implements Initializable {

    @FXML
    private Canvas canvas;
    private GraphicsContext gc;
    private Boolean showFPS = false;

    private PlayTimer animationTimer;
    private PlayGrid grid;
    private PlayGrid enemyGrid;

    public Map<String, JSONObject> clientMousePositions = new HashMap<>();
    private Boolean mouseDragging = false;
    private double mouseOffsetX, mouseOffsetY;

    public static Map<String, JSONObject> selectableObjects = new HashMap<>();
    private String selectedObject = "";

    //Grid var where they start
    private  int gridStartX = 25;
    private  int gridStartY = 25;

    private  int enemyGridStartX = 500;
    private  int enemyGridStartY = 25;


    @Override
    public void initialize(URL url, ResourceBundle rb) {

        // Get drawing context
        this.gc = canvas.getGraphicsContext2D();

        // Set listeners
        UtilsViews.parentContainer.heightProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
        UtilsViews.parentContainer.widthProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
        
        canvas.setOnMouseMoved(this::setOnMouseMoved);
        canvas.setOnMousePressed(this::onMousePressed);

        // Define grid
        grid = new PlayGrid(gridStartX, gridStartY, 25, 10, 10);
        enemyGrid  = new PlayGrid(enemyGridStartX, enemyGridStartY, 25, 10, 10);

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

    private void setOnMouseMoved(MouseEvent event) {
        double mouseX = event.getX();
        double mouseY = event.getY();

        JSONObject newPosition = new JSONObject();
        newPosition.put("x", mouseX);
        newPosition.put("y", mouseY);

        if (isMouseInsideGrid(grid, mouseX, mouseY)) {
            newPosition.put("col", grid.getCol(mouseX));
            newPosition.put("row", grid.getRow(mouseY));
        } else if (isMouseInsideGrid(enemyGrid, mouseX, mouseY)) {
            newPosition.put("col", enemyGrid.getCol(mouseX));
            newPosition.put("row", enemyGrid.getRow(mouseY));
        } else {
            newPosition.put("col", -1);
            newPosition.put("row", -1);
        }

        clientMousePositions.put(Main.clientId, newPosition);

        JSONObject msgObj = clientMousePositions.get(Main.clientId);
        msgObj.put("type", "clientMouseMoving");
        msgObj.put("clientId", Main.clientId);

        if (Main.wsClient != null) {
            Main.wsClient.safeSend(msgObj.toString());
        }
    }

    // Check if the mouse is inside a grid
    private boolean isMouseInsideGrid(PlayGrid grid, double mouseX, double mouseY) {
        return mouseX >= grid.getCellX(0) && mouseX < grid.getCellX((int) grid.getCols()) &&
               mouseY >= grid.getCellY(0) && mouseY < grid.getCellY((int) grid.getRows());
    }
    

    private void onMousePressed(MouseEvent event) {

        double mouseX = event.getX();
        double mouseY = event.getY();

        selectedObject = "";
        boolean mouseDragging = false;

        for (String objectId : selectableObjects.keySet()) {
            JSONObject obj = selectableObjects.get(objectId);
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

    public void setPlayersMousePositions(JSONObject positions) {
        clientMousePositions.clear();
        for (String clientId : positions.keySet()) {
            JSONObject positionObject = positions.getJSONObject(clientId);
            clientMousePositions.put(clientId, positionObject);
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

    // Run game (and animations)
    private void run(double fps) {

        if (animationTimer.fps < 1) { return; }

        // Update objects and animations here
    }

    // Draw game to canvas
    public void draw() {

        // Clean drawing area
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

         // Draw colored 'over' cells
        for (String clientId : clientMousePositions.keySet()) {
            JSONObject position = clientMousePositions.get(clientId);

            int col = position.getInt("col");
            int row = position.getInt("row");

            // Comprovar si està dins dels límits de la graella
            if (row >= 0 && col >= 0) {
                if ("A".equals(clientId)) {
                    gc.setFill(Color.LIGHTBLUE); 
                } else {
                    gc.setFill(Color.LIGHTGREEN); 
                }
                // Emplenar la casella amb el color clar
                PlayGrid targetGrid = isMouseInsideGrid(grid, position.getInt("x"), position.getInt("y")) ? grid : enemyGrid;
                gc.fillRect(targetGrid.getCellX(col), targetGrid.getCellY(row), targetGrid.getCellSize(), targetGrid.getCellSize());
            }
        }

        // Draw enemy Grid Hover
        drawGrid(grid, gridStartX, gridStartY);

        // Draw enemy Grid Hover
        drawGrid(enemyGrid, enemyGridStartX, enemyGridStartY);

        // Draw selectable objects
        for (String objectId : selectableObjects.keySet()) {
            JSONObject selectableObject = selectableObjects.get(objectId);
            drawSelectableObject(objectId, selectableObject);
        }

        // Draw mouse circles
        for (String clientId : clientMousePositions.keySet()) {
            JSONObject position = clientMousePositions.get(clientId);
            if ("A".equals(clientId)) {
                gc.setFill(Color.BLUE);
            } else {
                gc.setFill(Color.GREEN); 
            }
            gc.fillOval(position.getInt("x") - 5, position.getInt("y") - 5, 10, 10);
        }

        // Draw FPS if needed
        if (showFPS) { 
            animationTimer.drawFPS(gc); 
        }   
    }

    public void drawGrid(PlayGrid grid, double startX, double startY) {
        gc.setStroke(Color.BLACK);

        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                double cellSize = grid.getCellSize();
                double x = startX + col * cellSize;
                double y = startY + row * cellSize;
                gc.strokeRect(x, y, cellSize, cellSize);
            }
        }
    }

    public void drawSelectableObject(String objectId, JSONObject obj) {
        PlayGrid targetGrid = isMouseInsideGrid(grid, obj.getInt("x"), obj.getInt("y")) ? grid : enemyGrid;
        
        //Added default Red
        gc.setFill(Color.RED); 
        gc.fillRect(targetGrid.getCellX(obj.getInt("x")), targetGrid.getCellY(obj.getInt("y")), obj.getInt("cols") * targetGrid.getCellSize(), obj.getInt("rows") * targetGrid.getCellSize());
    }
}
