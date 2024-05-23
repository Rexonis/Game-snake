import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import java.sql.DriverManager;

public class Snake extends JPanel implements ActionListener {
    private final int WIDTH = 800;
    private final int HEIGHT = 600;
    private final int SEG_SIZE = 20;
    private final int DELAY = 100;

    @GameState
    private int gameState = GameState.MENU;
    private Timer timer;
    private Segment block;
    private Segment[] segments;
    private Segment head;
    private int snakeSize;
    private final int[][] mapping = { { 0, 1 }, { 1, 0 }, { 0, -1 }, { -1, 0 } };
    private int[] vector = {1, 0};
    private boolean keyPressInAction;
    private ScoreCounter scoreCounter;

    // Получение соединения с базой данных
    private final Connection connection = DatabaseConnection.getConnection();

    public Snake() {
        setBackground(Color.gray);
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (gameState == GameState.GAME) {
                    handleKeyPress(e);
                }
            }
        });
        try {
            connection.createStatement().execute("CREATE TABLE IF NOT EXISTS score_table (score INTEGER, player TEXT);");
        } catch (Exception e) {
            e.printStackTrace();
        }
        initGame();
    }

    private void initGame() {
        segments = new Segment[WIDTH * HEIGHT / (SEG_SIZE * SEG_SIZE)];
        snakeSize = 3;
        createBlock();
        for (int i = 0; i < snakeSize; i++) {
            segments[i] = new Segment(SEG_SIZE, SEG_SIZE);
        }
        head = segments[snakeSize - 1];
        timer = new Timer(DELAY, this);
        timer.start();
        scoreCounter = new ScoreCounter(); // Инициализация scoreCounter
    }

    private void createBlock() {
        Random random = new Random();
        int posX = SEG_SIZE * (1 + random.nextInt((WIDTH - SEG_SIZE) / SEG_SIZE));
        int posY = SEG_SIZE * (1 + random.nextInt((HEIGHT - SEG_SIZE) / SEG_SIZE));
        block = new Segment(posX, posY);
    }

    private void handleKeyPress(KeyEvent e) {
        if (keyPressInAction) {
            return;
        }
        keyPressInAction = true;
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_DOWN && vector[1] != -1) {
            vector = mapping[0];
        } else if (key == KeyEvent.VK_RIGHT && vector[0] != -1) {
            vector = mapping[1];
        } else if (key == KeyEvent.VK_UP && vector[1] != 1) {
            vector = mapping[2];
        } else if (key == KeyEvent.VK_LEFT && vector[0] != 1) {
            vector = mapping[3];
        }
        keyPressInAction = false;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gameState == GameState.GAME) {
            move();
            if (checkCollision()) {
                gameState = GameState.OVER;
                saveScoreToDatabase(); // Сохранение результата в базу данных
            } else if (head.x == block.x && head.y == block.y) {
                addSegment();
                createBlock();
                scoreCounter.incrementScore(); // Увеличение счета
            }
            repaint();
        }
    }

    private void saveScoreToDatabase() {
        try {
            String query = "INSERT INTO score_table (score,player) VALUES (?,?);";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, scoreCounter.getScore());
            statement.setString(2, scoreCounter.getPlayer());
            statement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void move() {
        for (int i = 0; i < snakeSize - 1; i++) {
            segments[i].x = segments[i + 1].x;
            segments[i].y = segments[i + 1].y;
        }
        head.x += vector[0] * SEG_SIZE;
        head.y += vector[1] * SEG_SIZE;
    }

    private boolean checkCollision() {
        if (head.x >= WIDTH || head.x < 0 || head.y < 0 || head.y >= HEIGHT) {
            return true;
        }
        for (int i = 0; i < snakeSize - 1; i++) {
            if (head.x == segments[i].x && head.y == segments[i].y) {
                return true;
            }
        }
        return false;
    }

    private void addSegment() {
        snakeSize++;
        head = new Segment(head.x, head.y);
        segments[snakeSize - 1] = head;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        draw(g);
    }

    private void draw(Graphics g) {
        switch (gameState) {
            case GameState.GAME -> {
                g.setColor(Color.RED);
                g.fillRect(block.x, block.y, SEG_SIZE, SEG_SIZE);

                g.setColor(Color.WHITE);
                for (int i = 0; i < snakeSize; i++) {
                    g.fillRect(segments[i].x, segments[i].y, SEG_SIZE, SEG_SIZE);
                }

                // Отображение счета
                g.setColor(Color.WHITE);
                g.drawString("Score: " + scoreCounter.getScore() + ", Player: " + scoreCounter.getPlayer(), 10, 40);
            }

            case GameState.OVER -> {
                removeAll();
                timer.stop();
                JLabel gameOverText = new JLabel("GAME OVER");
                gameOverText.setFont(new Font("Arial", Font.BOLD, 15));
                gameOverText.setForeground(Color.RED);
                gameOverText.setBounds(WIDTH / 2 - 50, HEIGHT / 2, 100, 20);
                add(gameOverText);

                JLabel scoreText = new JLabel("Score: " + scoreCounter.getScore() + ", Player: " + scoreCounter.getPlayer());
                scoreText.setFont(new Font("Arial", Font.BOLD, 15));
                scoreText.setForeground(Color.YELLOW);
                scoreText.setBounds(WIDTH / 2 - 50, 0, 200, 20);
                add(scoreText);

                JButton retry = new JButton("Replay");
                retry.setFont(new Font("Arial", Font.BOLD, 40));
                retry.setForeground(Color.BLUE);
                retry.setBounds(WIDTH / 2 - 100, HEIGHT / 2 + 30, 200, 60);
                retry.addActionListener(e -> {
                    segments = new Segment[WIDTH * HEIGHT / (SEG_SIZE * SEG_SIZE)];
                    snakeSize = 3;
                    createBlock();
                    for (int i = 0; i < snakeSize; i++) {
                        segments[i] = new Segment(SEG_SIZE, SEG_SIZE);
                    }
                    head = segments[snakeSize - 1];
                    scoreCounter.reset();
                    vector = new int[]{1, 0};
                    gameState = GameState.GAME;
                    timer.start();
                    removeAll();
                });
                add(retry);

                JButton exit = new JButton("Main Menu");
                exit.setFont(new Font("Arial", Font.BOLD, 30));
                exit.setForeground(Color.RED);
                exit.setBounds(WIDTH / 2 - 100, HEIGHT - 80, 200, 60);
                exit.addActionListener(e -> {
                    segments = new Segment[WIDTH * HEIGHT / (SEG_SIZE * SEG_SIZE)];
                    snakeSize = 3;
                    createBlock();
                    for (int i = 0; i < snakeSize; i++) {
                        segments[i] = new Segment(SEG_SIZE, SEG_SIZE);
                    }
                    head = segments[snakeSize - 1];
                    scoreCounter.reset();
                    vector = new int[]{1, 0};
                    gameState = GameState.MENU;
                    removeAll();
                    repaint();
                });
                add(exit);
            }

            case GameState.MENU -> {
                removeAll();
                timer.stop();
                JLabel title = new JLabel("Snake");
                title.setFont(new Font("Arial", Font.BOLD, 15));
                title.setForeground(Color.WHITE);
                title.setBounds(WIDTH / 2 - 50, 20, 100, 20);
                add(title);

                JTextField name = new JTextField(scoreCounter.getPlayer() != null ? scoreCounter.getPlayer() : "Player");
                name.setFont(new Font("Arial", Font.BOLD, 40));
                name.setForeground(Color.BLACK);
                name.setBounds(WIDTH / 2 - 100, HEIGHT / 2 - 30, 200, 60);
                add(name);

                JButton play = new JButton("Play");
                play.setFont(new Font("Arial", Font.BOLD, 40));
                play.setForeground(Color.BLACK);
                play.setBounds(WIDTH / 2 - 100, HEIGHT / 2 + 40, 200, 60);
                play.addActionListener(e -> {
                    scoreCounter.setPlayer(name.getText());
                    removeAll();
                    gameState = GameState.GAME;
                    timer.start();
                });
                add(play);

                JButton leaderboard = new JButton("Leaderboard");
                leaderboard.setFont(new Font("Arial", Font.BOLD, 40));
                leaderboard.setForeground(Color.BLUE);
                leaderboard.setBounds(WIDTH / 2 - 150, HEIGHT / 2 + 120, 300, 60);
                leaderboard.addActionListener(e -> {
                    removeAll();
                    gameState = GameState.LEADERBOARD;
                    repaint();
                });
                add(leaderboard);

                JButton exit = new JButton("Exit");
                exit.setFont(new Font("Arial", Font.BOLD, 40));
                exit.setForeground(Color.RED);
                exit.setBounds(WIDTH / 2 - 100, HEIGHT - 80, 200, 60);
                exit.addActionListener(e -> System.exit(0));
                add(exit);
            }

            case GameState.LEADERBOARD -> {
                JLabel title = new JLabel("LEADERBOARD");
                title.setFont(new Font("Arial", Font.BOLD, 15));
                title.setForeground(Color.WHITE);
                title.setBounds(WIDTH / 2 - 75, 20, 150, 20);
                add(title);

                List<Score> scores = getScoreFromDatabase();
                String[] columnNames = { "Score", "Player" };
                Object[][] dataScores = new Object[scores.size()][2];
                for (int i = 0; i < scores.size(); i++) {
                    dataScores[i][0] = scores.get(i).score;
                    dataScores[i][1] = scores.get(i).player;
                }
                JTable scoresView = new JTable(dataScores, columnNames);
                scoresView.setFont(new Font("Arial", Font.BOLD, 15));
                scoresView.setForeground(Color.BLACK);
                scoresView.setBounds(WIDTH / 2 - 75, 40, 150, HEIGHT - 80 - 60);
                add(scoresView);

                JButton exit = new JButton("Back");
                exit.setFont(new Font("Arial", Font.BOLD, 40));
                exit.setForeground(Color.RED);
                exit.setBounds(WIDTH / 2 - 100, HEIGHT - 80, 200, 60);
                exit.addActionListener(e -> {
                    removeAll();
                    gameState = GameState.MENU;
                    repaint();
                });
                add(exit);
            }
        }
    }

    private static class Segment {
        int x;
        int y;

        public Segment(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class ScoreCounter {
        private int score;
        private String player;

        public ScoreCounter() {
            score = 0;
        }

        public int getScore() {
            return score;
        }

        public String getPlayer() {
            return player;
        }

        public void setPlayer(String player) {
            this.player = player;
        }

        public void reset() {
            score = 0;
        }

        public void incrementScore() {
            score++;
        }
    }

    public static class Score {
        private int score;
        private String player;

        public Score(int score, String player) {
            this.score = score;
            this.player = player;
        }
    }

    private List<Score> getScoreFromDatabase() {
        try {
            String query = "SELECT score_table.score, score_table.player FROM score_table order by score_table.score desc;";
            var statement = connection.prepareStatement(query);
            var cursor = statement.executeQuery();
            List<Score> list = new ArrayList<>();
            while (cursor.next()) {
                list.add(new Score(cursor.getInt(1), cursor.getString(2)));
            }
            return list;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Snake Game");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.add(new Snake());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
