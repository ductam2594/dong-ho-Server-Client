package btl;

import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Client GUI:
 * - Hiển thị đồng hồ hệ thống
 * - Gửi yêu cầu TIME qua UDP đến server để đồng bộ
 * - Tab Đếm ngược (Countdown): nhập phút/giây -> Start / Pause / Reset
 * - Tab Alarm: đặt giờ/phút cụ thể -> khi tới sẽ beep + popup
 * - Ghi log qua FileUtils
 */
public class ClientGUI extends JFrame {
    // networking
    private JTextField txtServerIP;
    private JButton btnSync;
    private JCheckBox chkAutoSync;
    private Thread autoSyncThread;
    private volatile boolean autoSyncRunning = false;

    // clock
    private JLabel lblClock;
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    // log area
    private JTextArea txtLog;

    // countdown (client-side)
    private JLabel lblCountdown;
    private JSpinner spCntMin, spCntSec;
    private JButton btnCntStart, btnCntPause, btnCntReset;
    private javax.swing.Timer cntTimer;
    private long cntRemainingMs = 0;
    private boolean cntRunning = false;

    // alarm
    private JSpinner spHour, spMinute;
    private JButton btnSetAlarm, btnCancelAlarm;
    private volatile LocalTime alarmTime = null;
    private volatile boolean alarmSet = false;
    private Thread alarmThread;

    // config
    private static final int TIME_SYNC_INTERVAL_SEC = 30;
    private static final int SERVER_PORT = 5000;
    private static final int MAX_RETRIES = 3;

    public ClientGUI() {
        super("Client");
        initUI();
        startClockTick();
        setSize(640, 520);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                stopAutoSync();
                if (cntTimer != null) cntTimer.stop();
                alarmSet = false;
                FileUtils.appendLog("client.log", Utils.getCurrentDateTime() + " - Ứng dụng Client đã đóng.");
            }
        });

        FileUtils.appendLog("client.log", Utils.getCurrentDateTime() + " - Ứng dụng Client đã khởi động.");
        setVisible(true);
    }

    private void initUI() {
        JTabbedPane tabs = new JTabbedPane();

        // --- Clock tab ---
        JPanel clockPanel = new JPanel(new BorderLayout(8, 8));
        lblClock = new JLabel(Utils.getCurrentTime(), SwingConstants.CENTER);
        lblClock.setFont(new Font("Monospaced", Font.BOLD, 44));
        clockPanel.add(lblClock, BorderLayout.CENTER);

        JPanel topControls = new JPanel();
        topControls.add(new JLabel("Server IP:"));
        txtServerIP = new JTextField("127.0.0.1", 12);
        topControls.add(txtServerIP);
        btnSync = new JButton("Đồng bộ");
        chkAutoSync = new JCheckBox("Tự đồng bộ sau 30s");
        topControls.add(btnSync);
        topControls.add(chkAutoSync);
        clockPanel.add(topControls, BorderLayout.NORTH);

        // Log area
        txtLog = new JTextArea(8, 48);
        txtLog.setEditable(false);
        JScrollPane sp = new JScrollPane(txtLog);
        clockPanel.add(sp, BorderLayout.SOUTH);

        btnSync.addActionListener(e -> doSyncOnce());
        chkAutoSync.addActionListener(e -> {
            if (chkAutoSync.isSelected()) startAutoSync();
            else stopAutoSync();
        });

        tabs.add("Đồng hồ", clockPanel);

        // --- Countdown tab ---
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

        // --- Alarm tab ---
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

    private void doSyncOnce() {
        String ip = txtServerIP.getText().trim();
        if (ip.isEmpty()) {
            log("Đồng bộ thất bại: Không có địa chỉ IP server.");
            FileUtils.appendLog("client.log", Utils.getCurrentDateTime() + " - Đồng bộ thất bại: Không có địa chỉ IP server.");
            return;
        }

        try {
            InetAddress serverAddress = InetAddress.getByName(ip);
            for (int i = 0; i < MAX_RETRIES; i++) {
                try (DatagramSocket socket = new DatagramSocket()) {
                    byte[] sendData = "TIME\n".getBytes(StandardCharsets.UTF_8);
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, SERVER_PORT);
                    socket.send(sendPacket);

                    byte[] receiveData = new byte[1024];
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    socket.setSoTimeout(2000);
                    socket.receive(receivePacket);

                    String resp = new String(receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.UTF_8).trim();
                    if (!resp.isEmpty()) {
                        lblClock.setText(resp);
                        log("Đồng bộ thời gian thành công: " + resp);
                        FileUtils.appendLog("client.log", Utils.getCurrentDateTime() + " - Đồng bộ thời gian thành công: " + resp);
                        return;
                    }
                } catch (SocketTimeoutException ex) {
                    log("Đồng bộ thất bại (lần thử " + (i + 1) + "): Không nhận được phản hồi từ server.");
                    FileUtils.appendLog("client.log", Utils.getCurrentDateTime() + " - Đồng bộ thất bại (lần thử " + (i + 1) + "): Không nhận được phản hồi từ server.");
                    if (i < MAX_RETRIES - 1) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {}
                    }
                } catch (Exception ex) {
                    log("Lỗi đồng bộ: " + ex.getMessage());
                    FileUtils.appendLog("client.log", Utils.getCurrentDateTime() + " - Lỗi đồng bộ: " + ex.getMessage());
                    return;
                }
            }
            log("Đồng bộ thất bại sau " + MAX_RETRIES + " lần thử.");
            FileUtils.appendLog("client.log", Utils.getCurrentDateTime() + " - Đồng bộ thất bại sau " + MAX_RETRIES + " lần thử.");
        } catch (UnknownHostException ex) {
            log("Địa chỉ IP không hợp lệ: " + ex.getMessage());
            FileUtils.appendLog("client.log", Utils.getCurrentDateTime() + " - Địa chỉ IP không hợp lệ: " + ex.getMessage());
        }
    }

    private void startAutoSync() {
        if (autoSyncRunning) return;
        autoSyncRunning = true;
        autoSyncThread = new Thread(() -> {
            log("Bắt đầu tự đồng bộ mỗi " + TIME_SYNC_INTERVAL_SEC + " giây.");
            FileUtils.appendLog("client.log", Utils.getCurrentDateTime() + " - Bắt đầu tự đồng bộ mỗi " + TIME_SYNC_INTERVAL_SEC + " giây.");
            while (autoSyncRunning) {
                SwingUtilities.invokeLater(this::doSyncOnce);
                try {
                    Thread.sleep(TIME_SYNC_INTERVAL_SEC * 1000L);
                } catch (InterruptedException ignored) {}
            }
            autoSyncRunning = false;
            log("Đã dừng tự đồng bộ.");
            FileUtils.appendLog("client.log", Utils.getCurrentDateTime() + " - Đã dừng tự đồng bộ.");
        });
        autoSyncThread.setDaemon(true);
        autoSyncThread.start();
    }

    private void stopAutoSync() {
        autoSyncRunning = false;
        chkAutoSync.setSelected(false);
        log("Đã dừng tự đồng bộ.");
        FileUtils.appendLog("client.log", Utils.getCurrentDateTime() + " - Đã dừng tự đồng bộ.");
    }

    private void startCountdown() {
        if (cntRunning) return;
        int minutes = (Integer) spCntMin.getValue();
        int seconds = (Integer) spCntSec.getValue();
        cntRemainingMs = (minutes * 60L + seconds) * 1000L;
        if (cntRemainingMs <= 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập thời gian đếm ngược lớn hơn 0.");
            log("Đếm ngược thất bại: Thời gian không hợp lệ.");
            FileUtils.appendLog("client.log", Utils.getCurrentDateTime() + " - Đếm ngược thất bại: Thời gian không hợp lệ.");
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
                JOptionPane.showMessageDialog(this, "Hết giờ đếm ngược!");
                log("Đếm ngược đã hoàn tất.");
                FileUtils.appendLog("client.log", Utils.getCurrentDateTime() + " - Đếm ngược đã hoàn tất.");
            } else {
                lblCountdown.setText(Utils.formatDurationShort(cntRemainingMs));
            }
        });
        lblCountdown.setText(Utils.formatDurationShort(cntRemainingMs));
        cntTimer.start();
        cntRunning = true;
        log("Đã bắt đầu đếm ngược: " + Utils.formatDurationShort(cntRemainingMs));
        FileUtils.appendLog("client.log", Utils.getCurrentDateTime() + " - Đã bắt đầu đếm ngược: " + Utils.formatDurationShort(cntRemainingMs));
    }

    private void pauseCountdown() {
        if (!cntRunning) return;
        if (cntTimer != null) cntTimer.stop();
        cntRunning = false;
        log("Đã tạm dừng đếm ngược: " + Utils.formatDurationShort(cntRemainingMs));
        FileUtils.appendLog("client.log", Utils.getCurrentDateTime() + " - Đã tạm dừng đếm ngược: " + Utils.formatDurationShort(cntRemainingMs));
    }

    private void resetCountdown() {
        if (cntTimer != null) cntTimer.stop();
        cntRunning = false;
        cntRemainingMs = 0;
        lblCountdown.setText("00:00");
        log("Đã đặt lại đếm ngược.");
        FileUtils.appendLog("client.log", Utils.getCurrentDateTime() + " - Đã đặt lại đếm ngược.");
    }

    private void setAlarm() {
        int h = (Integer) spHour.getValue();
        int m = (Integer) spMinute.getValue();
        alarmTime = LocalTime.of(h, m);
        alarmSet = true;
        btnSetAlarm.setEnabled(false);
        btnCancelAlarm.setEnabled(true);
        log("Đã đặt báo thức cho " + alarmTime.toString());
        FileUtils.appendLog("client.log", Utils.getCurrentDateTime() + " - Đã đặt báo thức cho " + alarmTime.toString());

        alarmThread = new Thread(() -> {
            while (alarmSet) {
                LocalTime now = LocalTime.now().withSecond(0).withNano(0);
                if (now.equals(alarmTime)) {
                    Toolkit.getDefaultToolkit().beep();
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Báo thức: " + alarmTime.toString() + "!"));
                    alarmSet = false;
                    btnSetAlarm.setEnabled(true);
                    btnCancelAlarm.setEnabled(false);
                    log("Báo thức đã kích hoạt lúc " + Utils.getCurrentTime());
                    FileUtils.appendLog("client.log", Utils.getCurrentDateTime() + " - Báo thức đã kích hoạt lúc " + alarmTime.toString());
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
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
        log("Đã hủy báo thức.");
        FileUtils.appendLog("client.log", Utils.getCurrentDateTime() + " - Đã hủy báo thức.");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new ClientGUI();
            } catch (Exception e) {
                System.err.println("Lỗi khởi động ClientGUI: " + e.getMessage());
                FileUtils.appendLog("client.log", Utils.getCurrentDateTime() + " - Lỗi khởi động ClientGUI: " + e.getMessage());
            }
        });
    }
}