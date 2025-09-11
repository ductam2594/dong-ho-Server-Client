package btl;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class ServerGUI extends JFrame {
    private JLabel lblClock;
    private JTextArea txtLog;
    private JTable tblClients;
    private DefaultTableModel clientModel;

    private ServerSocket serverSocket;
    private ExecutorService pool;
    private boolean isRunning = false;

    public ServerGUI() {
        setTitle("Time Server");
        setSize(800, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Header
        JLabel header = new JLabel("TIME SERVER", SwingConstants.CENTER);
        header.setFont(new Font("Arial", Font.BOLD, 26));
        header.setForeground(Color.BLUE);
        add(header, BorderLayout.NORTH);

        // Clock
        lblClock = new JLabel("00:00:00", SwingConstants.CENTER);
        lblClock.setFont(new Font("Monospaced", Font.BOLD, 40));
        lblClock.setOpaque(true);
        lblClock.setBackground(Color.BLACK);
        lblClock.setForeground(Color.GREEN);
        add(lblClock, BorderLayout.CENTER);

        // Right: Client list
        String[] cols = {"Client IP", "Last Sync"};
        clientModel = new DefaultTableModel(cols, 0);
        tblClients = new JTable(clientModel);
        add(new JScrollPane(tblClients), BorderLayout.EAST);

        // Bottom: Log
        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setRows(8);
        add(new JScrollPane(txtLog), BorderLayout.SOUTH);

        // Left panel buttons
        JPanel leftPanel = new JPanel();
        JButton btnStart = new JButton("Start Server");
        JButton btnStop = new JButton("Stop Server");
        leftPanel.setLayout(new GridLayout(2, 1, 5, 5));
        leftPanel.add(btnStart);
        leftPanel.add(btnStop);
        add(leftPanel, BorderLayout.WEST);

        // Timer update clock
        new javax.swing.Timer(1000, e -> lblClock.setText(Utils.getCurrentTime())).start();

        // Actions
        btnStart.addActionListener(e -> startServer());
        btnStop.addActionListener(e -> stopServer());

        setVisible(true);
    }

    private void startServer() {
        if (isRunning) {
            log("Server đã chạy rồi!");
            return;
        }
        try {
            serverSocket = new ServerSocket(5000);
            pool = Executors.newCachedThreadPool();
            isRunning = true;
            log("Server đã khởi động tại cổng 5000...");

            pool.execute(() -> {
                while (isRunning) {
                    try {
                        Socket client = serverSocket.accept();
                        pool.execute(() -> handleClient(client));
                    } catch (IOException ex) {
                        if (isRunning) log("Lỗi: " + ex.getMessage());
                    }
                }
            });
        } catch (IOException e) {
            log("Không thể khởi động server: " + e.getMessage());
        }
    }

    private void stopServer() {
        if (!isRunning) {
            log("Server chưa chạy!");
            return;
        }
        try {
            isRunning = false;
            serverSocket.close();
            pool.shutdownNow();
            log("Server đã dừng.");
        } catch (IOException e) {
            log("Lỗi khi dừng server: " + e.getMessage());
        }
    }

    private void handleClient(Socket client) {
        String ip = client.getInetAddress().toString();
        log("Client kết nối: " + ip);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {

            String request = in.readLine();
            if ("TIME".equals(request)) {
                String now = Utils.getCurrentTime();
                out.println(now);

                // Log GUI
                log("Đã gửi thời gian " + now + " cho " + ip);

                // Ghi file
                FileUtils.writeLog("server_log.txt", "Client " + ip + " đồng bộ lúc " + now);

                SwingUtilities.invokeLater(() -> {
                    clientModel.addRow(new Object[]{ip, now});
                });
            }

        } catch (IOException e) {
            log("Lỗi client: " + e.getMessage());
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> txtLog.append(msg + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ServerGUI::new);
    }
}
