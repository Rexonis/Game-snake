import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Snake extends JPanel implements ActionListener {
    private final int WIDTH = 800;
    private final int HEIGHT = 600;
    private final int SEG_SIZE = 20;
    private final int DELAY = 100;
    private boolean IN_GAME = true;
    private Timer timer;
    private Segment block;
    private Segment[] segments;
    private Segment head;
    private int snakeSize;
    private int[][] mapping = { { 0, 1 }, { 1, 0 }, { 0, -1 }, { -1, 0 } };
    private int[] vector = { 1, 0 };
    private boolean keyPressInAction;
    private JLabel gameoverText;
    private ScoreCounter scoreCounter;
    // Получение соединения с базой данных
    private Connection connection = DatabaseConnection.getConnection();

    public Snake() {
        setBackground(Color.gray);
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyPress(e);
            }
        });
        initGame();
    }

    private void initGame() {
        IN_GAME = true;
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
        if (IN_GAME) {
            move();
            if (checkCollision()) {
                IN_GAME = false;
                saveScoreToDatabase(); // Сохранение результата в базу данных
            } else if (head.getX() == block.getX() && head.getY() == block.getY()) {
                addSegment();
                createBlock();
                scoreCounter.incrementScore(); // Увеличение счета
            }
            repaint();
        }
    }

    private void saveScoreToDatabase() {
        try {
            String query = "INSERT INTO score (score) VALUES (?)";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, scoreCounter.getScore());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void move() {
        for (int i = 0; i < snakeSize - 1; i++) {
            segments[i].setX(segments[i + 1].getX());
            segments[i].setY(segments[i + 1].getY());
        }
        head.setX(head.getX() + vector[0] * SEG_SIZE);
        head.setY(head.getY() + vector[1] * SEG_SIZE);
    }

    private boolean checkCollision() {
        if (head.getX() >= WIDTH || head.getX() < 0 || head.getY() < 0 || head.getY() >= HEIGHT) {
            return true;
        }
        for (int i = 0; i < snakeSize - 1; i++) {
            if (head.getX() == segments[i].getX() && head.getY() == segments[i].getY()) {
                return true;
            }
        }
        return false;
    }

    private void addSegment() {
        snakeSize++;
        head = new Segment(head.getX(), head.getY());
        segments[snakeSize - 1] = head;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        draw(g);
    }

    private void draw(Graphics g) {
        if (IN_GAME) {
            g.setColor(Color.RED);
            g.fillRect(block.getX(), block.getY(), SEG_SIZE, SEG_SIZE);

            g.setColor(Color.WHITE);
            for (int i = 0; i < snakeSize; i++) {
                g.fillRect(segments[i].getX(), segments[i].getY(), SEG_SIZE, SEG_SIZE);
            }

            // Отображение счета
            g.setColor(Color.WHITE);
            g.drawString("Score: " + scoreCounter.getScore(), 10, 20);
        } else {
            gameoverText = new JLabel("GAME OVER");
            gameoverText.setFont(new Font("Arial", Font.BOLD, 15));
            gameoverText.setForeground(Color.RED);
            gameoverText.setBounds(WIDTH / 2 - 50, HEIGHT / 2, 100, 20);
            add(gameoverText);

            //var scoreText = new JLabel("Score:");
            //scoreText.setFont(new Font("Arial", Font.BOLD, 15));
            //scoreText.setForeground(Color.YELLOW);
            //scoreText.setBounds(WIDTH / 2 - 50, 0, 100, 20);
            //add(scoreText);

            //getScoreToDatabase();

        }
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

    private class Segment {
        private int x;
        private int y;

        public Segment(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public void setX(int x) {
            this.x = x;
        }

        public void setY(int y) {
            this.y = y;
        }
    }

    public class ScoreCounter {
        private int score;

        public ScoreCounter() {
            this.score = 0;
        }

        public int getScore() {
            return score;
        }

        public void incrementScore() {
            this.score++;
        }
    }

    //private void getScoreToDatabase() {
    //    try {
    //        String query = "SELECT `score` FROM score.score order by `score` desc";
    //       PreparedStatement statement = connection.prepareStatement(query);
    //        var lol = statement.executeQuery();
    //        System.out.println();
    //   } catch (SQLException e) {
    //        e.printStackTrace();
    //   }
    // }
}
