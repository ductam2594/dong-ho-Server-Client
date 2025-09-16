package btl;

import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server GUI:
 * - Lắng nghe port 5000, khi nhận "TIME" qua UDP, trả về current time (HH:mm:ss)
 * - Tab Countdown (server-local): giống client
 * - Tab Alarm (server-local)
 * - Ghi log file server.log
 */
public class ServerGUI extends JFrame {
    private JTextArea txtLog;
    private JButton btnStart, btnStop;
    private AtomicBoolean running = new AtomicBoolean(false);
    private Thread serverThread;

    // Clock (server-local)
    private JLabel lblClock;

    // Countdown (server local)
    private JLabel lblCountdown;
    private JSpinner spCntMin, spCntSec;
    private JButton btnCntStart, btnCntPause, btnCntReset;
    private javax.swing.Timer cntTimer;
    private long cntRemainingMs = 0;
    private boolean cntRunning = false;

    // Alarm
    private JSpinner spHour, spMinute;
    private JButton btnSetAlarm, btnCancelAlarm;
    private volatile LocalTime alarmTime = null;
    private volatile boolean alarmSet = false;
    private Thread alarmThread;

    private static final int PORT = 5000;

    public ServerGUI() {
        super("Server");
        initUI();
        startClockTick();
        setSize(640, 520);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
        FileUtils.appendLog("server.log", Utils.getCurrentDateTime() + " - Giao diện Server đã khởi động.");
    }

    private void initUI() {
        JTabbedPane tabs = new JTabbedPane();

        // Server tab
        JPanel serverPanel = new JPanel(new BorderLayout(8, 8));
        JPanel top = new JPanel();
        btnStart = new JButton("Start Server");
        btnStop = new JButton("Stop Server");
        btnStop.setEnabled(false);
        top.add(btnStart);
        top.add(btnStop);
        serverPanel.add(top, BorderLayout.NORTH);

        // Add clock display
        lblClock = new JLabel(Utils.getCurrentTime(), SwingConstants.CENTER);
        lblClock.setFont(new Font("Monospaced", Font.BOLD, 44));
        serverPanel.add(lblClock, BorderLayout.CENTER);

        txtLog = new JTextArea(10, 48);
        txtLog.setEditable(false);
        serverPanel.add(new JScrollPane(txtLog), BorderLayout.SOUTH);
        btnStart.addActionListener(e -> startServer());
        btnStop.addActionListener(e -> stopServer());
        tabs.add("Server", serverPanel);

        // Countdown tab (server-local)
        JPanel cntPanel = new JPanel(new BorderLayout(8, 8));
        lblCountdown = new JLabel("00:00", SwingConstants.CENTER);
        lblCountdown.setFont(new Font("Monospaced", Font.BOLD, 48));
        cntPanel.add(lblCountdown, BorderLayout.CENTER);

        JPanel cntTop = new JPanel();
        spCntMin = new JSpinner(new SpinnerNumberModel(0, 0, 999, 1));
        spCntSec = new JSpinner(new SpinnerNumberModel(0, 0, 59, 1));
        cntTop.add(new JLabel("Phút:"));
        cntTop.add(spCntMin);
        cntTop.add(new JLabel("Giây:"));
        cntTop.add(spCntSec);
        cntPanel.add(cntTop, BorderLayout.NORTH);

        JPanel cntButtons = new JPanel();
        btnCntStart = new JButton("Start");
        btnCntPause = new JButton("Pause");
        btnCntReset = new JButton("Reset");
        cntButtons.add(btnCntStart);
        cntButtons.add(btnCntPause);
        cntButtons.add(btnCntReset);
        cntPanel.add(cntButtons, BorderLayout.SOUTH);

        btnCntStart.addActionListener(e -> startCountdown());
        btnCntPause.addActionListener(e -> pauseCountdown());
        btnCntReset.addActionListener(e -> resetCountdown());

        tabs.add("Đếm ngược", cntPanel);

        // Alarm tab
        JPanel alarmPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        spHour = new JSpinner(new SpinnerNumberModel(LocalTime.now().getHour(), 0, 23, 1));
        spMinute = new JSpinner(new SpinnerNumberModel(LocalTime.now().getMinute(), 0, 59, 1));
        btnSetAlarm = new JButton("Đặt báo thức");
        btnCancelAlarm = new JButton("Hủy báo thức");
        btnCancelAlarm.setEnabled(false);
        c.gridx = 0;
        c.gridy = 0;
        alarmPanel.add(new JLabel("Giờ:"), c);
        c.gridx = 1;
        alarmPanel.add(spHour, c);
        c.gridx = 0;
        c.gridy = 1;
        alarmPanel.add(new JLabel("Phút:"), c);
        c.gridx = 1;
        alarmPanel.add(spMinute, c);
        c.gridx = 0;
        c.gridy = 2;
        alarmPanel.add(btnSetAlarm, c);
        c.gridx = 1;
        alarmPanel.add(btnCancelAlarm, c);
        btnSetAlarm.addActionListener(e -> setAlarm());
        btnCancelAlarm.addActionListener(e -> cancelAlarm());
        tabs.add("Báo thức", alarmPanel);

        getContentPane().add(tabs, BorderLayout.CENTER);
    }

    private void startClockTick() {
        new javax.swing.Timer(1000, e -> lblClock.setText(Utils.getCurrentTime())).start();
    }

    private void log(String s) {
        SwingUtilities.invokeLater(() -> {
            txtLog.append("[" + Utils.getCurrentDateTime() + "] " + s + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    private void startServer() {
        if (running.get()) return;
        running.set(true);
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        serverThread = new Thread(() -> {
            log("Server đang khởi động trên cổng " + PORT);
            FileUtils.appendLog("server.log", Utils.getCurrentDateTime() + " - Server đang khởi động trên cổng " + PORT);
            try (DatagramSocket socket = new DatagramSocket(PORT)) {
                byte[] buffer = new byte[1024];
                while (running.get()) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String received = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                    String remote = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                    if ("TIME".equalsIgnoreCase(received)) {
                        String now = Utils.getCurrentTime();
                        byte[] response = (now + "\n").getBytes(StandardCharsets.UTF_8);
                        DatagramPacket responsePacket = new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort());
                        socket.send(responsePacket);
                        log("Đã đồng bộ thời gian -> " + now + " tới " + remote);
                        FileUtils.appendLog("server.log", Utils.getCurrentDateTime() + " - Đã đồng bộ thời gian " + remote + " -> " + now);
                    } else {
                        byte[] response = "UNKNOWN\n".getBytes(StandardCharsets.UTF_8);
                        DatagramPacket responsePacket = new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort());
                        socket.send(responsePacket);
                        log("Yêu cầu không xác định: " + received + " từ " + remote);
                    }
                }
            } catch (Exception ex) {
                log("Lỗi Server: " + ex.getMessage());
                FileUtils.appendLog("server.log", Utils.getCurrentDateTime() + " - Lỗi Server: " + ex.getMessage());
            }
            log("Server đã dừng.");
            FileUtils.appendLog("server.log", Utils.getCurrentDateTime() + " - Server đã dừng.");
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void stopServer() {
        running.set(false);
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        log("Đang dừng Server...");
    }

    private void startCountdown() {
        if (cntRunning) return;
        int minutes = (Integer) spCntMin.getValue();
        int seconds = (Integer) spCntSec.getValue();
        cntRemainingMs = (minutes * 60L + seconds) * 1000L;
        if (cntRemainingMs <= 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập thời gian lớn hơn 0.");
            return;
        }

        cntTimer = new javax.swing.Timer(250, e -> {
            cntRemainingMs -= 250;
            if (cntRemainingMs <= 0) {
                cntRemainingMs = 0;
                lblCountdown.setText("00:00");
                cntTimer.stop();
                cntRunning = false;
                Toolkit.getDefaultToolkit().beep();
                JOptionPane.showMessageDialog(this, "⏱️ Server: Hết giờ!");
                log("Đếm ngược Server đã hoàn tất.");
                FileUtils.appendLog("server.log", Utils.getCurrentDateTime() + " - Đếm ngược Server đã hoàn tất.");
            } else {
                lblCountdown.setText(Utils.formatDurationShort(cntRemainingMs));
            }
        });
        lblCountdown.setText(Utils.formatDurationShort(cntRemainingMs));
        cntTimer.start();
        cntRunning = true;
        log("Đếm ngược Server đã bắt đầu: " + Utils.formatDurationShort(cntRemainingMs));
        FileUtils.appendLog("server.log", Utils.getCurrentDateTime() + " - Đếm ngược Server đã bắt đầu -> " + Utils.formatDurationShort(cntRemainingMs));
    }

    private void pauseCountdown() {
        if (!cntRunning) return;
        if (cntTimer != null) cntTimer.stop();
        cntRunning = false;
        log("Đếm ngược Server đã tạm dừng: " + Utils.formatDurationShort(cntRemainingMs));
        FileUtils.appendLog("server.log", Utils.getCurrentDateTime() + " - Đếm ngược Server đã tạm dừng -> " + Utils.formatDurationShort(cntRemainingMs));
    }

    private void resetCountdown() {
        if (cntTimer != null) cntTimer.stop();
        cntRunning = false;
        cntRemainingMs = 0;
        lblCountdown.setText("00:00");
        log("Đếm ngược Server đã được đặt lại.");
        FileUtils.appendLog("server.log", Utils.getCurrentDateTime() + " - Đếm ngược Server đã được đặt lại.");
    }

    private void setAlarm() {
        int h = (Integer) spHour.getValue();
        int m = (Integer) spMinute.getValue();
        alarmTime = LocalTime.of(h, m);
        alarmSet = true;
        btnSetAlarm.setEnabled(false);
        btnCancelAlarm.setEnabled(true);
        log("Báo thức Server được đặt cho " + alarmTime.toString());
        FileUtils.appendLog("server.log", Utils.getCurrentDateTime() + " - Báo thức Server được đặt cho " + alarmTime.toString());

        alarmThread = new Thread(() -> {
            while (alarmSet) {
                LocalTime now = LocalTime.now().withSecond(0).withNano(0);
                if (now.equals(alarmTime)) {
                    Toolkit.getDefaultToolkit().beep();
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Báo thức Server! " + alarmTime.toString()));
                    alarmSet = false;
                    btnSetAlarm.setEnabled(true);
                    btnCancelAlarm.setEnabled(false);
                    log("Báo thức Server được kích hoạt lúc " + Utils.getCurrentTime());
                    FileUtils.appendLog("server.log", Utils.getCurrentDateTime() + " - Báo thức Server được kích hoạt lúc " + alarmTime.toString());
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
        });
        alarmThread.setDaemon(true);
        alarmThread.start();
    }

    private void cancelAlarm() {
        alarmSet = false;
        alarmTime = null;
        btnSetAlarm.setEnabled(true);
        btnCancelAlarm.setEnabled(false);
        log("Báo thức Server đã bị hủy.");
        FileUtils.appendLog("server.log", Utils.getCurrentDateTime() + " - Báo thức Server đã bị hủy.");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new ServerGUI();
            } catch (Exception e) {
                System.err.println("Error starting ServerGUI: " + e.getMessage());
                FileUtils.appendLog("server.log", Utils.getCurrentDateTime() + " - Error starting ServerGUI: " + e.getMessage());
            }
        });
    }
}