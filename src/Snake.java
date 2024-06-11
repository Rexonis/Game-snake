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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class Snake extends JPanel implements ActionListener {
    private final int WIDTH = 800; // Ширина игрового поля
    private final int HEIGHT = 600; // Высота игрового поля
    private final int SEG_SIZE = 20; // Размер клетки змейки
    private final int DELAY = 100;

    @GameState
    private int gameState = GameState.MENU;
    private Timer timer;
    private Segment block;
    private Segment[] segments;
    private Segment head; // Объект головы змейки
    private int snakeSize;
    private final int[][] mapping = { { 0, 1 }, { 1, 0 }, { 0, -1 }, { -1, 0 } }; // Массив хранения значений возможных направлений змейки
    private int[] vector = {1, 0}; // Вектор для хранения текущегго напрвления змейки
    private boolean keyPressInAction;
    private ScoreCounter scoreCounter;
    private final Connection connection = DatabaseConnection.getConnection(); // Получение соединения с базой данных

    public Snake() {
        setBackground(Color.gray);  // Установка фона в серый цвет
        setPreferredSize(new Dimension(WIDTH, HEIGHT)); // Установка предпочтительного размера для панели
        setFocusable(true);
        addKeyListener(new KeyAdapter() { // Добавление обработчика событий клавиатуры
            @Override
            public void keyPressed(KeyEvent e) {
                // Проверка, находится ли игрок в состоянии игры
                if (gameState == GameState.GAME) {
                    // Обработка нажатия клавиш
                    handleKeyPress(e);
                }
            }
        });
        // Создание таблицы в базе данных для хранения очков игроков
        try {
            connection.createStatement().execute("CREATE TABLE IF NOT EXISTS score_table (score INTEGER, player TEXT);");
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Инициализация игры
        initGame();
    }

    // Инициализация переменных и объектов для управления игрой
    private void initGame() {
        // Создание массива объектов Segment для представления клеток змейки
        segments = new Segment[WIDTH * HEIGHT / (SEG_SIZE * SEG_SIZE)];
        // Установка размера змейки в 3 клетки (начальный размер змейки)
        snakeSize = 3;
        // Создание блока для игры
        createBlock();
        // Инициализация головы змейки
        for (int i = 0; i < snakeSize; i++) {
            segments[i] = new Segment(SEG_SIZE, SEG_SIZE);
        }
        head = segments[snakeSize - 1];
        timer = new Timer(DELAY, this);
        timer.start();
        // Инициализация объекта счетчика очков
        scoreCounter = new ScoreCounter();
    }

    // Создание случайной позиции блока на игровом поле
    private void createBlock() {
        Random random = new Random();
        // Генерация случайной позиции блока по X-оси
        int posX = SEG_SIZE * (1 + random.nextInt((WIDTH - SEG_SIZE) / SEG_SIZE));
        // Генерация случайной позиции блока по Y-оси
        int posY = SEG_SIZE * (1 + random.nextInt((HEIGHT - SEG_SIZE) / SEG_SIZE));
        // Создание блока на игровом поле
        block = new Segment(posX, posY);
    }

    // Обработка нажатия клавиш и изменение направления движения змейки
    private void handleKeyPress(KeyEvent e) {
        // Проверка, находится ли игрок в состоянии игры
        if (keyPressInAction) {
            // Возвращение, если игрок уже нажал клавишу
            return;
        }
        // Установка флага для обработки нажатия клавишу
        keyPressInAction = true;
        // Получение кода нажатой клавишу
        int key = e.getKeyCode();
        // Проверка, была ли нажата клавиша вниз и текущее направление не равно -1
        if (key == KeyEvent.VK_DOWN && vector[1] != -1) {
            // Изменение направления на вверх
            vector = mapping[0];
        } else if (key == KeyEvent.VK_RIGHT && vector[0] != -1) {
            // Изменение направления на вправо
            vector = mapping[1];
        } else if (key == KeyEvent.VK_UP && vector[1] != 1) {
            // Изменение направления на вверх
            vector = mapping[2];
        } else if (key == KeyEvent.VK_LEFT && vector[0] != 1) {
            // Изменение направления на влево
            vector = mapping[3];
        }
        // Установка флага для обработки нажатия клавишу в false
        keyPressInAction = false;
    }

    // Обработка событий таймера
    @Override
    public void actionPerformed(ActionEvent e) {
        // Проверка, находится ли игрок в состоянии игры
        if (gameState == GameState.GAME) {
            // Движение змейки
            move();
            // Проверка на столкновение змейки с блоком или стеной
            if (checkCollision()) {
                // Завершение игры и сохранение результата в базу данных
                gameState = GameState.OVER;
                saveScoreToDatabase(); // Сохранение результата в базу данных
            } else if (head.x == block.x && head.y == block.y) {
                // Добавление новой клетки змейки и создание нового блока
                addSegment();
                createBlock();
                scoreCounter.incrementScore(); // Увеличение счета
            }
            // Отрисовка обновленной игровой панели
            repaint();
        }
    }

    // Сохранение результата игры в базу данных
    private void saveScoreToDatabase() {
        try {
            // Формирование запроса для вставки нового записи в таблицу score_table
            String query = "INSERT INTO score_table (score,player) VALUES (?,?);";
            // Создание объекта PreparedStatement для выполнения запроса
            PreparedStatement statement = connection.prepareStatement(query);
            // Установка значения счета и имени игрока в запрос
            statement.setInt(1, scoreCounter.getScore());
            statement.setString(2, scoreCounter.getPlayer());
            // Выполнение запроса
            statement.executeUpdate();
        } catch (Exception e) {
            // Вывод ошибки в консоль
            e.printStackTrace();
        }
    }

    // Движение змейки
    private void move() {
        // Переход всех клеток змейки на одну клетку в направлении движения
        for (int i = 0; i < snakeSize - 1; i++) {
            segments[i].x = segments[i + 1].x;
            segments[i].y = segments[i + 1].y;
        }
        // Переход головы змейки на одну клетку в направлении движения
        head.x += vector[0] * SEG_SIZE;
        head.y += vector[1] * SEG_SIZE;
    }

    // Проверка на столкновение змейки с блоком или стеной
    private boolean checkCollision() {
        // Проверка на столкновение со стеной
        if (head.x >= WIDTH || head.x < 0 || head.y < 0 || head.y >= HEIGHT) {
            // Возвращение true, если произошло столкновение со стеной
            return true;
        }
        // Проверка на столкновение с телом змейки
        for (int i = 0; i < snakeSize - 1; i++) {
            if (head.x == segments[i].x && head.y == segments[i].y) {
                // Возвращение true, если произошло столкновение с телом змейки
                return true;
            }
        }
        // Возвращение false, если произошло столкновение с блоком
        return false;
    }

    // Добавление нового сегмента змейки
    private void addSegment() {
        // Увеличение размера змейки
        snakeSize++;
        // Создание нового сегмента с текущими координатами головы
        head = new Segment(head.x, head.y);
        // Добавление нового сегмента в массив сегментов
        segments[snakeSize - 1] = head;
    }

    // Переопределенный метод для рисования элементов на экране
    @Override
    protected void paintComponent(Graphics g) {
        // Вызов метода рисования
        super.paintComponent(g);
        draw(g);
    }

    // Метод для рисования элементов на экране
    private void draw(Graphics g) {
        // Определение текущего состояния игры
        switch (gameState) {
            // Рисование блока и змейки в случае игры
            case GameState.GAME -> {
                // Рисование блока
                g.setColor(Color.RED);
                g.fillRect(block.x, block.y, SEG_SIZE, SEG_SIZE);

                // Рисование змейки
                g.setColor(Color.WHITE);
                for (int i = 0; i < snakeSize; i++) {
                    g.fillRect(segments[i].x, segments[i].y, SEG_SIZE, SEG_SIZE);
                }

                // Отображение счета
                g.setColor(Color.WHITE);
                g.drawString("Score: " + scoreCounter.getScore() + ", Player: " + scoreCounter.getPlayer(), 10, 40);
            }

            // Рисование сообщения "GAME OVER" и счета в случае завершения игры
            case GameState.OVER -> {
                // Очистка экрана от предыдущих компонентов
                removeAll();
                timer.stop();
                // Создание объекта "GAME OVER" и добавление его на экран
                JLabel gameOverText = new JLabel("GAME OVER");
                gameOverText.setFont(new Font("Arial", Font.BOLD, 15));
                gameOverText.setForeground(Color.RED);
                gameOverText.setBounds(WIDTH / 2 - 50, HEIGHT / 2, 100, 20);
                add(gameOverText);

                // Создание объекта с счетом и добавление его на экран
                JLabel scoreText = new JLabel("Score: " + scoreCounter.getScore() + ", Player: " + scoreCounter.getPlayer());
                scoreText.setFont(new Font("Arial", Font.BOLD, 15));
                scoreText.setForeground(Color.YELLOW);
                scoreText.setBounds(WIDTH / 2 - 50, 0, 200, 20);
                add(scoreText);

                // Создание кнопки "Replay" и добавление ее на экран
                JButton retry = new JButton("Replay");
                retry.setFont(new Font("Arial", Font.BOLD, 40));
                retry.setForeground(Color.BLUE);
                retry.setBounds(WIDTH / 2 - 100, HEIGHT / 2 + 30, 200, 60);
                retry.addActionListener(e -> {
                    // Очистка экрана и начало новой игры
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

                // Создание кнопки "Main Menu" и добавление ее на экран
                JButton exit = new JButton("Main Menu");
                exit.setFont(new Font("Arial", Font.BOLD, 30));
                exit.setForeground(Color.RED);
                exit.setBounds(WIDTH / 2 - 100, HEIGHT - 80, 200, 60);
                exit.addActionListener(e -> {
                    // Очистка экрана и переход в меню
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

            // Рисование меню в случае его отображения
            case GameState.MENU -> {
                // Удаление всех компонентов на экране
                removeAll();
                timer.stop(); // Остановка таймера
                // Создание объекта "Snake" и добавление его на экран
                JLabel title = new JLabel("Snake");
                title.setFont(new Font("Arial", Font.BOLD, 15));
                title.setForeground(Color.WHITE);
                title.setBounds(WIDTH / 2 - 50, 20, 100, 20); // Расположение объекта Snake
                add(title);

                // Создание текстового поля для ввода имени игрока и добавление его на экран
                JTextField name = new JTextField(scoreCounter.getPlayer() != null ? scoreCounter.getPlayer() : "Игрок");
                name.setFont(new Font("Arial", Font.BOLD, 40));
                name.setForeground(Color.BLACK);
                name.setBounds(WIDTH / 2 - 100, HEIGHT / 2 - 30, 200, 60);
                add(name);

                // Создание кнопки "Play" и добавление ее на экран
                JButton play = new JButton("Play");
                play.setFont(new Font("Arial", Font.BOLD, 40));
                play.setForeground(Color.BLACK);
                play.setBounds(WIDTH / 2 - 100, HEIGHT / 2 + 40, 200, 60);
                play.addActionListener(e -> {
                    // Установка имени игрока и начало новой игры
                    scoreCounter.setPlayer(name.getText());
                    removeAll();
                    gameState = GameState.GAME;
                    timer.start();
                });
                add(play);

                // Создание кнопки "Leaderboard" и добавление ее на экран
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

                // Создание кнопки "Exit" и добавление ее на экран
                JButton exit = new JButton("Exit");
                exit.setFont(new Font("Arial", Font.BOLD, 40));
                exit.setForeground(Color.RED);
                exit.setBounds(WIDTH / 2 - 100, HEIGHT - 80, 200, 60);
                exit.addActionListener(e -> System.exit(0));
                add(exit);
            }

            // Создание объекта "leaderboard" и добавление его на экран
            case GameState.LEADERBOARD -> {
                JLabel title = new JLabel("LEADERBOARD");
                title.setFont(new Font("Arial", Font.BOLD, 15));
                title.setForeground(Color.WHITE);
                title.setBounds(WIDTH / 2 - 75, 20, 150, 20);
                add(title);



                // Создание кнопки "Back" и добавление ее на экран
                JButton exit = new JButton("Back");
                exit.setFont(new Font("Arial", Font.BOLD, 40));
                exit.setForeground(Color.RED);
                exit.setBounds(WIDTH / 2 - 100, HEIGHT - 80, 200, 60);
                exit.addActionListener(e -> {
                    // Очистка экрана и переход в меню
                    removeAll();
                    gameState = GameState.MENU;
                    repaint();
                });
                add(exit);
            }
        }
    }

    // Класс Segment
    private static class Segment {
        int x;
        int y;

        public Segment(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    // Класс ScoreCounter
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

    // Класс Score
    public static class Score {
        private int score;
        private String player;

        public Score(int score, String player) {
            this.score = score;
            this.player = player;
        }
    }

    // Метод для получения списка очков из базы данных
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

    // Основной метод программы
    public static void main(String[] args) {
        // Использование SwingUtilities для запуска GUI в отдельном потоке
        SwingUtilities.invokeLater(() -> {
            // Создание объекта JFrame и инициализация его
            JFrame frame = new JFrame("Snake Game");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            // Добавление объекта Snake на JFrame
            frame.add(new Snake());
            // Установка размеров JFrame по его содержанию
            frame.pack();
            // Централизация JFrame на экране
            frame.setLocationRelativeTo(null);
            // Отображение JFrame
            frame.setVisible(true);
        });
    }
}
