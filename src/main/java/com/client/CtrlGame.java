package com.client;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import org.json.JSONArray;
import org.json.JSONObject;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.util.Duration;

public class CtrlGame implements Initializable {

    @FXML
    private Canvas canvas;
    private GraphicsContext gc;
    private final Boolean showFPS = false;
    @FXML
    private Label timerLabel;
    @FXML
    private Label turnLabel;

    // Players
    private PlayTimer animationTimer;
    private PlayGrid player1Grid;
    private PlayGrid player2Grid; 

    // Mouse position
    public Map<String, JSONObject> clientMousePositions = new HashMap<>();

    // Grid starting positions for both players
    private final int gridStartX = 25; 
    private final int gridStartY = 25;

    private final int enemyGridStartX = 500;
    private final int enemyGridStartY = 25;

    // Player ships information
    private final Map<String, int[]> player1Ships = new HashMap<>(); 
    private final Map<String, int[]> player2Ships = new HashMap<>();

    // Track hit status for each player
    private final boolean[][] player1ClickStatus = new boolean[10][10]; // Cells clicked by player 1
    private final boolean[][] player2ClickStatus = new boolean[10][10]; // Cells clicked by player 2

    private final Color[][] player1CellColors = new Color[10][10]; // Colors cells clicked by player 1
    private final Color[][] player2CellColors = new Color[10][10]; // Colors cells clicked by player 2

    // Track clicks and what they did 
    private double lastClickedX = -1; 
    private double lastClickedY = -1; 
    private boolean isHit = false; 
    private boolean isSunk = false; 

    // Turn timer
    private Timeline turnTimer;
    //private final int turnDuration = 60; // Commented for test
    private final int turnDuration = 20; 
    private int timeRemaining = turnDuration; 
    private boolean isPlayerATurn = true; // Flag to indicate if it's player A turn

    /*-------------------------- Start code --------------------------*/

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        // Get drawing context
        this.gc = canvas.getGraphicsContext2D();


        // Set listeners
        UtilsViews.parentContainer.heightProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
        UtilsViews.parentContainer.widthProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
        
        canvas.setOnMouseMoved(this::setOnMouseMoved);
        canvas.setOnMouseClicked(this::onMouseClicked);
        if (Main.wsClient != null) {
            Main.wsClient.onMessage(this::wsMessage);
        }

        // Define grid
        player1Grid = new PlayGrid(gridStartX, gridStartY, 25, 10, 10);
        player2Grid  = new PlayGrid(enemyGridStartX, enemyGridStartY, 25, 10, 10);

        // Start run/draw timer bucle
        animationTimer = new PlayTimer(this::run, this::draw, 0);
        start();

        //Initialize countdown for turns
        //TODO - this should be managed by server
        turnTimer = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            timeRemaining--;
            timerLabel.setText("Remaining Time: " + timeRemaining + "s");
            if (timeRemaining <= 0) {
                switchTurn(); 
            }
        }));
        turnTimer.setCycleCount(Timeline.INDEFINITE);

        startTurnTimer();

    }

    public void setupShips(JSONObject objects, String player) {
        Map<String, int[]> targetShips;

        if (player.equals("player1")) {
            targetShips = player1Ships;
        } else {
            targetShips = player2Ships;
        }
        targetShips.clear();
        for (String objectId : objects.keySet()) {
            JSONArray positionArray = objects.getJSONArray(objectId);  
            int[] positionObject = new int[positionArray.length()];  
            for (int i = 0; i < positionArray.length(); i++) {
                positionObject[i] = positionArray.getInt(i);  
            }
            targetShips.put(objectId, positionObject); 
        }
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

    

    /*-------------------------- Control Mouse actions --------------------------*/

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

    private void onMouseClicked(MouseEvent event) {
        double mouseX = event.getX();
        double mouseY = event.getY();
    
        if ((isPlayerATurn && Main.isPlayer1) || (!isPlayerATurn && !Main.isPlayer1)) {
            PlayGrid targetGrid = null;
            Map<String, int[]> targetShips = null;
            Color[][] targetColors;
            
        
            if (isMouseInsideGrid(player1Grid, mouseX, mouseY) && !Main.isPlayer1) {
                targetGrid = player1Grid;
                targetShips = player1Ships;
                targetColors = player1CellColors;
            } else if (isMouseInsideGrid(player2Grid, mouseX, mouseY) && Main.isPlayer1) {
                targetGrid = player2Grid;
                targetShips = player2Ships;
                targetColors = player2CellColors;
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
                lastClickedX = targetGrid.getCellX(col);
                lastClickedY = targetGrid.getCellY(row);
                
                // HIT
                if (isShipHit(targetShips, row, col)) {
                    System.out.println("Tocado");
                    isHit = true;
                    targetColors[row][col] = Color.YELLOW;

                    // Sunk
                    if (isShipSunk(targetGrid,targetShips, row, col)) {
                        System.out.println("Hundido");
                        isSunk = true;

                        int[][] sunkCells = getShipCells(targetShips, row, col);
                        for (int[] cell : sunkCells) {
                            int sunkRow = cell[0];
                            int sunkCol = cell[1];
                            targetColors[sunkRow][sunkCol] = Color.RED;
                        }

                        if (areAllShipsSunk(targetShips, targetGrid)) {
                            showVictoryScreen();
                        }
                    }

                    startTurnTimer();
                } else {
                    // Water
                    System.out.println("Fallo");

                    isHit = false;
                    targetColors[row][col] = Color.BLUE;                    
                    isSunk = false;

                    switchTurn();
                }

                sendShootMsg(col,row);
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

    /*-------------------------- Draw Grid/items logic --------------------------*/

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
         if (Main.isPlayer1) {
           drawPlayerShips(player1Grid, player1Ships);
        } else {
            drawPlayerShips(player2Grid, player2Ships);
        }
        
        //Hover
        drawHoverEffect();

        // Draw mouse circles
        for (String clientId : clientMousePositions.keySet()) {
            JSONObject position = clientMousePositions.get(clientId);
            if (Main.isPlayer1) {
                gc.setFill(Color.BLUE);
            } else {
                gc.setFill(Color.GREEN);
            }
            gc.fillOval(position.getInt("x") - 5, position.getInt("y") - 5, 10, 10);
        }

        //Draw color in cells on click

        drawHitCells(player1Grid, player1CellColors);
        drawHitCells(player2Grid, player2CellColors);

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

    

    private void drawHitCells(PlayGrid grid, Color[][] cellColors) {
        double cellSize = grid.getCellSize();
        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                Color cellColor = cellColors[row][col];
                if (cellColor != null) {
                    double x = grid.getCellX(col);
                    double y = grid.getCellY(row);
                    gc.setFill(cellColor);
                    gc.fillRect(x, y, cellSize, cellSize);
                    gc.strokeRect(x, y, cellSize, cellSize);
                }
            }
        }
    }

    /*-------------------------- Ship/cell management logic --------------------------*/


    private int[][] getShipCells(Map<String, int[]> ships, int hitRow, int hitCol) {
        for (int[] ship : ships.values()) {
            int startRow = ship[0];
            int startCol = ship[1];
            int length = ship[2];
            int orientation = ship[3];
            
            int[][] shipCells = new int[length][2];
            boolean containsHit = false;
    
            for (int i = 0; i < length; i++) {
                int row = startRow + (orientation == 1 ? i : 0);
                int col = startCol + (orientation == 0 ? i : 0);
                shipCells[i] = new int[] { row, col };
    
                if (row == hitRow && col == hitCol) {
                    containsHit = true;
                }
            }
    
            if (containsHit) {
                return shipCells; 
            }
        }
        return new int[0][0]; 
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

    private boolean areAllShipsSunk(Map<String, int[]> ships, PlayGrid targetGrid) {
        for (int[] ship : ships.values()) {
            int startRow = ship[0];
            int startCol = ship[1];
            int length = ship[2];
            int orientation = ship[3];
            boolean isSunk = true;
    
            for (int i = 0; i < length; i++) {
                int row = startRow + (orientation == 1 ? i : 0);
                int col = startCol + (orientation == 0 ? i : 0);
    
                if (!isCellClicked(targetGrid, row, col)) {
                    isSunk = false;
                    break;
                }
            }
    
            if (!isSunk) {
                return false; 
            }
        }
        return true; 
    }

    /*-------------------------- Timer logic --------------------------*/


    //Turn Timer
    private void startTurnTimer() {
        timeRemaining = turnDuration; 
        timerLabel.setText("Remaining Time: " + timeRemaining + "s");
        turnTimer.playFromStart();
    }

    private void switchTurn() {
        isPlayerATurn = !isPlayerATurn; 
        timeRemaining = turnDuration; 
        timerLabel.setText("Remaining Time: " + timeRemaining + "s"); 

        turnLabel.setText(isPlayerATurn ? "Player A turn" : "Player B turn");

        turnTimer.playFromStart(); 

    }


    /*-------------------------- Server Requests logic --------------------------*/

    private void sendShootMsg(int col, int row){
        JSONObject shotMessage = new JSONObject();
        shotMessage.put("type", "playerShot");
        shotMessage.put("clientId", Main.clientId);
        shotMessage.put("x", col);
        shotMessage.put("y", row);
    
        if (Main.wsClient != null) {
            Main.wsClient.safeSend(shotMessage.toString());
        }
    }

    public void wsMessage(String message) {
        JSONObject obj = new JSONObject(message);
        
        if ("shotResult".equals(obj.getString("type"))) {

            updateBoardWithShotResult(obj);
        }
    }

    public void updateBoardWithShotResult(JSONObject obj) {
        String result = obj.getString("result"); // "hit", "miss", "sunk"
        String clientId = obj.getString("clientId");
        int col = obj.getInt("x");
        int row = obj.getInt("y");
    
        Color[][] targetColors;
        
        if (Main.isPlayer1) {
            targetColors =player1CellColors ;
        } else {
            targetColors = player2CellColors; 
        }
    
        if(!Main.clientId.equals(clientId)){
            switch (result) {
                case "hit":
                    targetColors[row][col] = Color.YELLOW;
                    break;
                case "sunk":
                    JSONArray sunkCells = obj.getJSONArray("sunkCells");
                    for (int i = 0; i < sunkCells.length(); i++) {
                        JSONObject cell = sunkCells.getJSONObject(i);
                        int sunkRow = cell.getInt("y");
                        int sunkCol = cell.getInt("x");
                        targetColors[sunkRow][sunkCol] = Color.RED;
                    }
                    break;
                case "miss":
                    targetColors[row][col] = Color.BLUE;
                    break;
                default:
                    break;
            }
        
            draw();
        }
    } 
    
    /*-------------------------- End game logic --------------------------*/

    private void showVictoryScreen() {
        //stop();
    
        System.out.println("¡Victoria! Has hundido todos los barcos enemigos.");
        
    }
}
