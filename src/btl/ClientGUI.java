package btl;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.Timer;
import java.util.TimerTask;

public class ClientGUI extends JFrame {
    private JLabel lblClock;
    private JTextField txtServerIP;
    private JButton btnConnect, btnSync;
    private JCheckBox chkAuto;
    private JTextArea txtLog;

    private boolean connected = false;
    private String serverIP = "127.0.0.1";
    private Timer autoSyncTimer;

    public ClientGUI() {
        setTitle("Time Client");
        setSize(500, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Header
        JLabel header = new JLabel("TIME CLIENT", SwingConstants.CENTER);
        header.setFont(new Font("Arial", Font.BOLD, 26));
        header.setForeground(Color.ORANGE);
        add(header, BorderLayout.NORTH);

        // Clock
        lblClock = new JLabel("00:00:00", SwingConstants.CENTER);
        lblClock.setFont(new Font("Monospaced", Font.BOLD, 40));
        lblClock.setOpaque(true);
        lblClock.setBackground(Color.BLACK);
        lblClock.setForeground(Color.GREEN);
        add(lblClock, BorderLayout.CENTER);

        // Control Panel
        JPanel control = new JPanel(new FlowLayout());
        txtServerIP = new JTextField(serverIP, 10);
        btnConnect = new JButton("Kết nối");
        btnSync = new JButton("Đồng bộ");
        chkAuto = new JCheckBox("Tự động 30s");
        control.add(new JLabel("Server IP:"));
        control.add(txtServerIP);
        control.add(btnConnect);
        control.add(btnSync);
        control.add(chkAuto);
        add(control, BorderLayout.NORTH);

        // Log
        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setRows(8);
        add(new JScrollPane(txtLog), BorderLayout.SOUTH);

        // Timer update local clock
        new javax.swing.Timer(1000, e -> lblClock.setText(Utils.getCurrentTime())).start();

        // Actions
        btnConnect.addActionListener(e -> connectServer());
        btnSync.addActionListener(e -> syncTime());
        chkAuto.addActionListener(e -> toggleAutoSync());

        setVisible(true);
    }

    private void connectServer() {
        serverIP = txtServerIP.getText().trim();
        connected = true;
        log("Đã kết nối tới server: " + serverIP);
    }

    private void syncTime() {
        if (!connected) {
            log("Chưa kết nối server!");
            return;
        }
        try (Socket socket = new Socket(serverIP, 5000);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            out.println("TIME");
            String time = in.readLine();
            if (time != null) {
                lblClock.setText(time);
                log("Đồng bộ thành công, giờ server: " + time);

                // Ghi file log
                FileUtils.writeLog("client_log.txt", "Đồng bộ với server " + serverIP + " lúc " + time);
            }
        } catch (IOException e) {
            log("Lỗi khi đồng bộ: " + e.getMessage());
        }
    }

    private void toggleAutoSync() {
        if (chkAuto.isSelected()) {
            autoSyncTimer = new Timer();
            autoSyncTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    SwingUtilities.invokeLater(() -> syncTime());
                }
            }, 0, 30000);
            log("Bật tự động đồng bộ mỗi 30s.");
        } else {
            if (autoSyncTimer != null) autoSyncTimer.cancel();
            log("Tắt tự động đồng bộ.");
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> txtLog.append(msg + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientGUI::new);
    }
}
