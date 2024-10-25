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
    private PlayGrid player1Grid;
    private PlayGrid player2Grid;

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

    //PlayerShips
    private Map<String,int[]> player1Ships = new HashMap<>();
    private Map<String,int[]> player2Ships = new HashMap<>();

    private boolean[][] player1ClickStatus = new boolean[10][10];
    private boolean[][] player2ClickStatus = new boolean[10][10];

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        // Get drawing context
        this.gc = canvas.getGraphicsContext2D();

        // Set listeners
        UtilsViews.parentContainer.heightProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
        UtilsViews.parentContainer.widthProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
        
        canvas.setOnMouseMoved(this::setOnMouseMoved);
        canvas.setOnMouseClicked(this::onMouseClicked);

        // Define grid
        player1Grid = new PlayGrid(gridStartX, gridStartY, 25, 10, 10);
        player2Grid  = new PlayGrid(enemyGridStartX, enemyGridStartY, 25, 10, 10);

        // Player 1 ships
        Map<String, int[]> player1ShipsData = new HashMap<>();
        player1ShipsData.put("ship1", new int[]{0, 0, 5, 0}); 
        player1ShipsData.put("ship2", new int[]{2, 2, 3, 1}); 
        loadPlayerShips("player1", player1ShipsData);

        // Player 2 ships
        Map<String, int[]> player2ShipsData = new HashMap<>();
        player2ShipsData.put("ship1", new int[]{1, 1, 4, 0}); 
        player2ShipsData.put("ship2", new int[]{3, 5, 2, 1}); 
        loadPlayerShips("player2", player2ShipsData);

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

        if (isMouseInsideGrid(player1Grid, mouseX, mouseY)) {
            newPosition.put("col", player1Grid.getCol(mouseX));
            newPosition.put("row", player1Grid.getRow(mouseY));
        } else if (isMouseInsideGrid(player2Grid, mouseX, mouseY)) {
            newPosition.put("col", player2Grid.getCol(mouseX));
            newPosition.put("row", player2Grid.getRow(mouseY));
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

    //TODO - Print colors doesnt work on grid but mouse is being detected correctly
    private void onMouseClicked(MouseEvent event) {
        double mouseX = event.getX();
        double mouseY = event.getY();
    
        PlayGrid targetGrid = null;
        Map<String, int[]> targetShips = null;
    
        if (isMouseInsideGrid(player1Grid, mouseX, mouseY) && "B".equals(Main.clientId)) {
            targetGrid = player1Grid;
            targetShips = player1Ships;
        } else if (isMouseInsideGrid(player2Grid, mouseX, mouseY) && "A".equals(Main.clientId)) {
            targetGrid = player2Grid;
            targetShips = player2Ships;
        } else {
            return;
        }
    
        int col = targetGrid.getCol(mouseX);
        int row = targetGrid.getRow(mouseY);
    
        System.out.println("Row: " + row + ", Col: " + col);
        
        // Only with cells not clicked never
        if (!isCellClicked(targetGrid, row, col)) {
            markCellAsClicked(targetGrid, row, col);
    
            System.out.println("Drawing at: (" + targetGrid.getCellX(col) + ", " + targetGrid.getCellY(row) + ")");
            // HIT
            if (isShipHit(targetShips, row, col)) {
                System.out.println("Tocado");

                gc.setFill(Color.YELLOW);
                gc.fillRect(targetGrid.getCellX(col), targetGrid.getCellY(row), targetGrid.getCellSize(), targetGrid.getCellSize());
                gc.strokeRect(targetGrid.getCellX(col), targetGrid.getCellY(row), targetGrid.getCellSize(), targetGrid.getCellSize());
    
                // Sunk
                if (isShipSunk(targetGrid,targetShips, row, col)) {
                    System.out.println("Hundido");
                    gc.setFill(Color.RED);
                    gc.fillRect(targetGrid.getCellX(col), targetGrid.getCellY(row), targetGrid.getCellSize(), targetGrid.getCellSize());
                    gc.strokeRect(targetGrid.getCellX(col), targetGrid.getCellY(row), targetGrid.getCellSize(), targetGrid.getCellSize());
                }
            } else {
                // Water
                System.out.println("Fallo");

                gc.setFill(Color.BLUE);
                gc.fillRect(targetGrid.getCellX(col), targetGrid.getCellY(row), targetGrid.getCellSize(), targetGrid.getCellSize());
                gc.strokeRect(targetGrid.getCellX(col), targetGrid.getCellY(row), targetGrid.getCellSize(), targetGrid.getCellSize());
            }
        }
    }
        
    // Check cell as clicked
    private boolean isCellClicked(PlayGrid grid, int row, int col) {
        if (grid == player1Grid) {
            return player1ClickStatus[row][col];
        } else if (grid == player2Grid) {
            return player2ClickStatus[row][col];
        }
        return false; 
    }
    
    // Mark cell as clicked
    private void markCellAsClicked(PlayGrid grid, int row, int col) {
        if (grid == player1Grid) {
            player1ClickStatus[row][col] = true;
        } else if (grid == player2Grid) {
            player2ClickStatus[row][col] = true;
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
        double cellSize = player1Grid.getCellSize();
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

    public void drawHoverEffect() {
        JSONObject mousePosition = clientMousePositions.get(Main.clientId);
        if (mousePosition == null) return;

        int col = mousePosition.getInt("col");
        int row = mousePosition.getInt("row");
        double mouseX = mousePosition.getDouble("x");
        double mouseY = mousePosition.getDouble("y");

        PlayGrid targetGrid;
        if (isMouseInsideGrid(player1Grid, mouseX, mouseY)) {
            targetGrid = player1Grid;
        } else if (isMouseInsideGrid(player2Grid, mouseX, mouseY)) {
            targetGrid = player2Grid;
        } else {
            return;
        }

        if (row >= 0 && col >= 0 && row < targetGrid.getRows() && col < targetGrid.getCols()) {
            double x = targetGrid.getCellX(col);
            double y = targetGrid.getCellY(row);
            double cellSize = targetGrid.getCellSize();

            gc.setFill(Color.color(0.5, 0.5, 0.5, 0.3));
            gc.fillRect(x, y, cellSize, cellSize);

            gc.setStroke(Color.BLACK);
            gc.strokeRect(x, y, cellSize, cellSize);
        }
    }

    // Draw game to canvas
    public void draw() {
        // Clean drawing area
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Draw grids
        drawGrid(player1Grid, gridStartX, gridStartY);
        drawGrid(player2Grid, enemyGridStartX, enemyGridStartY);

         // Draw Ships
         if ("A".equals(Main.clientId)) {
           drawPlayerShips(player1Grid, player1Ships);
        } else {
            drawPlayerShips(player2Grid, player2Ships);
        }
        
        //Hover
        drawHoverEffect();

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

        String[] letters = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J"};

        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                double cellSize = grid.getCellSize();
                double x = startX + col * cellSize;
                double y = startY + row * cellSize;

                gc.strokeRect(x, y, cellSize, cellSize);
                
                if (row == 0 && col < letters.length) {
                    gc.setFill(Color.BLACK);
                    gc.fillText(letters[col], x + cellSize / 2 - 5, startY - 5);
                }

                if (col == 0) {
                    gc.setFill(Color.BLACK); 
                    gc.fillText(String.valueOf(row + 1), startX - 15, y + cellSize / 2 + 5);
                }
            }
        }
    }

    public void drawPlayerShips(PlayGrid grid, Map<String, int[]> ships) {
        gc.setFill(Color.GRAY);

        for (String shipId : ships.keySet()) {
            int[] shipInfo = ships.get(shipId);
            int startRow = shipInfo[0];
            int startCol = shipInfo[1];
            int length = shipInfo[2];
            int orientation = shipInfo[3];

            for (int i = 0; i < length; i++) {
                int row = startRow + (orientation == 1 ? i : 0);
                int col = startCol + (orientation == 0 ? i : 0);

                double x = grid.getCellX(col);
                double y = grid.getCellY(row);

                gc.fillRect(x, y, grid.getCellSize(), grid.getCellSize());
                gc.strokeRect(x, y, grid.getCellSize(), grid.getCellSize());
            }
        }
    }

    public void loadPlayerShips(String playerId, Map<String, int[]> ships) {
        if (playerId.equals("player1")) {
            player1Ships.clear();
            player1Ships.putAll(ships); 
        } else if (playerId.equals("player2")) {
            player2Ships.clear();
            player2Ships.putAll(ships); 
        }
    }

    private boolean isShipHit(Map<String, int[]> ships, int row, int col) {
        for (int[] ship : ships.values()) {
            int startRow = ship[0];
            int startCol = ship[1];
            int length = ship[2];
            int orientation = ship[3];
    
            for (int i = 0; i < length; i++) {
                int shipRow = startRow + (orientation == 1 ? i : 0);
                int shipCol = startCol + (orientation == 0 ? i : 0);
    
                if (shipRow == row && shipCol == col) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isShipSunk(PlayGrid targetGrid,Map<String, int[]> ships, int hitRow, int hitCol) {
        for (int[] ship : ships.values()) {
            int startRow = ship[0];
            int startCol = ship[1];
            int length = ship[2];
            int orientation = ship[3];
            boolean isSunk = true;
    
            for (int i = 0; i < length; i++) {
                int row = startRow + (orientation == 1 ? i : 0);
                int col = startCol + (orientation == 0 ? i : 0);
    
                if (!isCellClicked(targetGrid,row, col)) {
                    isSunk = false;
                    break;
                }
            }
    
            if (isSunk && (startRow <= hitRow && hitRow < startRow + length) && (startCol <= hitCol && hitCol < startCol + length)) {
                return true;
            }
        }
        return false;
    }

    public void drawSelectableObject(String objectId, JSONObject obj) {
        PlayGrid targetGrid = isMouseInsideGrid(player1Grid, obj.getInt("x"), obj.getInt("y")) ? player1Grid : player2Grid;

        gc.setFill(Color.RED);
        gc.fillRect(targetGrid.getCellX(obj.getInt("x")), targetGrid.getCellY(obj.getInt("y")), obj.getInt("cols") * targetGrid.getCellSize(), obj.getInt("rows") * targetGrid.getCellSize());
    }
}
