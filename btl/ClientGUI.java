// File: ClientGUI.java
package btl;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * UDP Time Client GUI (btl)
 * - Tab: Đồng bộ | Đếm ngược | Báo thức | Nhật ký
 * - Giải pháp: chỉ 1 luồng receive chung + BlockingQueue để phân phối phản hồi
 */
public class ClientGUI extends JFrame {
    private DatagramSocket socket = null;
    private String serverHost = "localhost";
    private int serverPort = 9876;
    private final int SOCKET_TIMEOUT_MS = 3000;

    // Incoming messages queue (listener nhận và push vào đây)
    private final BlockingQueue<String> incomingMessages = new LinkedBlockingQueue<>();

    // UI
    private final JLabel lblLocalClock = new JLabel("--:--:--", SwingConstants.CENTER);
    private final JLabel lblServerTime = new JLabel("Chưa đồng bộ", SwingConstants.CENTER);
    private final JLabel lblLocalZone = new JLabel("Múi giờ cục bộ: " + ZoneId.systemDefault().getId(), SwingConstants.CENTER);

    // Bảng nhật ký
    private final DefaultTableModel activityModel = new DefaultTableModel(new String[]{"Thời gian", "Sự kiện"}, 0);

    // Scheduler
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private ScheduledFuture<?> autoSyncFuture;

    // Đếm ngược
    private ScheduledFuture<?> countdownFuture;
    private volatile long countdownRemaining = 0;
    private final JLabel lblCountdownBig = new JLabel("00:00:00", SwingConstants.CENTER);

    // Báo thức
    private final DefaultListModel<AlarmItem> alarmListModel = new DefaultListModel<>();
    private final JLabel lblAlarmClock = new JLabel("--:--:--", SwingConstants.CENTER);

    // Đồng hồ kim
    private AnalogClockPanel analogClock;

    // Biến lưu trữ độ lệch thời gian và múi giờ đã đồng bộ
    private volatile long timeOffsetMillis = 0;
    private volatile ZoneId syncedZoneId = ZoneId.systemDefault();

    // Listener thread reference (so we can interrupt/stop if needed)
    private Thread listenerThread;

    public ClientGUI() {
        super("Ứng dụng đồng hồ (Client)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1300, 620);
        setLocationRelativeTo(null);

        initSocket();

        // Khởi động listener chung (luồng receive duy nhất)
        startSocketListener();

        initUI();
        startLocalClock();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
    }

    private void initSocket() {
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            logActivity("UDP socket tạo tại cổng " + socket.getLocalPort());
        } catch (Exception e) {
            logActivity("Lỗi khởi tạo socket: " + e.getMessage());
            socket = null;
        }
    }

    private void initUI() {
        UIManager.put("Panel.background", new Color(240, 248, 255));
        UIManager.put("Button.background", new Color(135, 206, 250));
        UIManager.put("Button.foreground", Color.BLACK);
        UIManager.put("Button.font", new Font("SansSerif", Font.BOLD, 12));
        UIManager.put("Label.font", new Font("SansSerif", Font.PLAIN, 12));
        UIManager.put("TitledBorder.font", new Font("SansSerif", Font.BOLD, 14));
        UIManager.put("TitledBorder.titleColor", new Color(25, 25, 112));

        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        main.setBackground(new Color(240, 248, 255));

        JPanel top = new JPanel(new GridBagLayout());
        top.setBackground(new Color(240, 248, 255));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.BOTH;

        // Đồng hồ cục bộ - Có cả đồng hồ kim và số
        JPanel localCard = new JPanel(new BorderLayout());
        localCard.setBackground(Color.WHITE);
        TitledBorder localBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(65, 105, 225)),
                "Đồng hồ cục bộ");
        localBorder.setTitleFont(new Font("SansSerif", Font.BOLD, 16));
        localBorder.setTitleColor(new Color(65, 105, 225));
        localCard.setBorder(localBorder);

        analogClock = new AnalogClockPanel();
        analogClock.setBackground(Color.WHITE);
        lblLocalClock.setFont(new Font("Monospaced", Font.BOLD, 40));
        lblLocalClock.setForeground(new Color(34, 139, 34));
        lblLocalZone.setFont(new Font("Monospaced", Font.PLAIN, 12));
        lblLocalZone.setForeground(new Color(100, 100, 100));

        JPanel localClockDisplay = new JPanel(new BorderLayout());
        localClockDisplay.add(lblLocalClock, BorderLayout.CENTER);
        localClockDisplay.add(lblLocalZone, BorderLayout.SOUTH);
        localClockDisplay.setBackground(Color.WHITE);

        localCard.add(analogClock, BorderLayout.CENTER);
        localCard.add(localClockDisplay, BorderLayout.SOUTH);
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.4; gbc.weighty = 0.3;
        top.add(localCard, gbc);

        // Đồng bộ Server
        JPanel serverCard = new JPanel(new BorderLayout());
        serverCard.setBackground(Color.WHITE);
        TitledBorder serverBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(220, 20, 60)),
                "Thời gian từ Server / Đồng bộ");
        serverBorder.setTitleFont(new Font("SansSerif", Font.BOLD, 16));
        serverBorder.setTitleColor(new Color(220, 20, 60));
        serverCard.setBorder(serverBorder);
        lblServerTime.setFont(new Font("Monospaced", Font.BOLD, 36));
        lblServerTime.setForeground(new Color(178, 34, 34));
        serverCard.add(lblServerTime, BorderLayout.CENTER);

        JPanel syncControls = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        syncControls.setBackground(Color.WHITE);
        final JTextField tfHost = new JTextField(serverHost, 14);
        final JSpinner spnPort = new JSpinner(new SpinnerNumberModel(serverPort, 1024, 65535, 1));
        JButton btnSyncNow = new JButton("Đồng bộ ngay");
        JButton btnPing = new JButton("Ping");
        final JCheckBox chkAuto = new JCheckBox("Tự động đồng bộ mỗi");
        final JSpinner spnAutoSec = new JSpinner(new SpinnerNumberModel(10, 2, 3600, 1));

        syncControls.add(new JLabel("Máy chủ:")); syncControls.add(tfHost);
        syncControls.add(new JLabel("Cổng:")); syncControls.add(spnPort);
        syncControls.add(btnSyncNow); syncControls.add(btnPing);
        syncControls.add(chkAuto); syncControls.add(spnAutoSec); syncControls.add(new JLabel("giây"));

        serverCard.add(syncControls, BorderLayout.SOUTH);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.6; gbc.weighty = 0.3;
        top.add(serverCard, gbc);

        main.add(top, BorderLayout.NORTH);

        // Tabs trung tâm
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(new Color(240, 248, 255));
        tabs.setOpaque(true);

        // Tab đếm ngược
        JPanel countdownPanel = new JPanel(new BorderLayout(8, 8));
        countdownPanel.setBackground(new Color(240, 248, 255));
        JPanel cntTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        cntTop.setBackground(new Color(240, 248, 255));
        final JSpinner spnCountdownHr = new JSpinner(new SpinnerNumberModel(0, 0, 99, 1));
        final JSpinner spnCountdownMin = new JSpinner(new SpinnerNumberModel(0, 0, 59, 1));
        final JSpinner spnCountdownSec = new JSpinner(new SpinnerNumberModel(0, 0, 59, 1));
        JButton btnCntStart = new JButton("Bắt đầu");
        JButton btnCntStop = new JButton("Dừng");
        cntTop.add(new JLabel("Giờ:")); cntTop.add(spnCountdownHr);
        cntTop.add(new JLabel("Phút:")); cntTop.add(spnCountdownMin);
        cntTop.add(new JLabel("Giây:")); cntTop.add(spnCountdownSec);
        cntTop.add(btnCntStart); cntTop.add(btnCntStop);

        lblCountdownBig.setFont(new Font("Monospaced", Font.BOLD, 64));
        lblCountdownBig.setForeground(new Color(255, 140, 0));
        countdownPanel.add(cntTop, BorderLayout.NORTH);
        countdownPanel.add(lblCountdownBig, BorderLayout.CENTER);
        tabs.addTab("Đếm ngược", countdownPanel);

        // Tab báo thức
        JPanel alarmPanel = new JPanel(new BorderLayout(8, 8));
        alarmPanel.setBackground(new Color(240, 248, 255));

        JPanel alTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        alTop.setBackground(new Color(240, 248, 255));
        final JSpinner spnAlarmHour = new JSpinner(new SpinnerNumberModel(LocalTime.now().getHour(), 0, 23, 1));
        final JSpinner spnAlarmMin = new JSpinner(new SpinnerNumberModel(LocalTime.now().getMinute(), 0, 59, 1));
        JButton btnSetAlarm = new JButton("Cài báo thức");
        JButton btnRemoveAlarm = new JButton("Hủy báo thức đã chọn");
        JButton btnCancelAllAlarms = new JButton("Hủy tất cả");

        alTop.add(new JLabel("Giờ:")); alTop.add(spnAlarmHour);
        alTop.add(new JLabel("Phút:")); alTop.add(spnAlarmMin);
        alTop.add(btnSetAlarm);

        JPanel alarmListPanel = new JPanel(new BorderLayout(5, 5));
        alarmListPanel.setBackground(new Color(240, 248, 255));
        JList<AlarmItem> alarmList = new JList<>(alarmListModel);
        alarmList.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane alarmScrollPane = new JScrollPane(alarmList);
        alarmScrollPane.setBorder(BorderFactory.createTitledBorder("Các báo thức đã đặt"));

        JPanel alarmButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        alarmButtonPanel.setBackground(new Color(240, 248, 255));
        alarmButtonPanel.add(btnRemoveAlarm);
        alarmButtonPanel.add(btnCancelAllAlarms);

        alarmListPanel.add(alarmScrollPane, BorderLayout.CENTER);
        alarmListPanel.add(alarmButtonPanel, BorderLayout.SOUTH);

        JPanel alarmDisplayPanel = new JPanel(new BorderLayout());
        alarmDisplayPanel.setBackground(new Color(240, 248, 255));
        lblAlarmClock.setFont(new Font("Monospaced", Font.BOLD, 64));
        lblAlarmClock.setForeground(new Color(150, 50, 150));

        alarmDisplayPanel.add(lblAlarmClock, BorderLayout.CENTER);

        alarmPanel.add(alTop, BorderLayout.NORTH);
        alarmPanel.add(alarmDisplayPanel, BorderLayout.CENTER);
        alarmPanel.add(alarmListPanel, BorderLayout.EAST);

        tabs.addTab("Báo thức", alarmPanel);

        // Tab nhật ký
        JTable activityTable = new JTable(activityModel);
        activityTable.setFillsViewportHeight(true);
        JScrollPane spActivity = new JScrollPane(activityTable);
        spActivity.setBorder(new TitledBorder("Nhật ký hoạt động"));
        tabs.addTab("Nhật ký", spActivity);

        main.add(tabs, BorderLayout.CENTER);

        add(main);

        // ===== Sự kiện =====
        btnSyncNow.addActionListener(ae -> {
            serverHost = tfHost.getText().trim();
            serverPort = ((Number) spnPort.getValue()).intValue();
            syncOnce();
        });

        btnPing.addActionListener(ae -> {
            serverHost = tfHost.getText().trim();
            serverPort = ((Number) spnPort.getValue()).intValue();
            pingServer();
        });

        chkAuto.addActionListener(ae -> {
            if (chkAuto.isSelected()) {
                int sec = ((Number) spnAutoSec.getValue()).intValue();
                scheduleAutoSync(sec);
            } else {
                cancelAutoSync();
            }
        });

        btnCntStart.addActionListener(ae -> {
            try {
                long h = (Integer) spnCountdownHr.getValue();
                long m = (Integer) spnCountdownMin.getValue();
                long s = (Integer) spnCountdownSec.getValue();
                long totalSeconds = h * 3600 + m * 60 + s;
                startCountdown(totalSeconds);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Nhập thời gian hợp lệ", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnCntStop.addActionListener(ae -> stopCountdown());

        btnSetAlarm.addActionListener(ae -> {
            int h = (Integer) spnAlarmHour.getValue();
            int m = (Integer) spnAlarmMin.getValue();
            setServerAlarm(h, m);
        });

        btnRemoveAlarm.addActionListener(ae -> {
            AlarmItem selectedItem = alarmList.getSelectedValue();
            if (selectedItem != null) {
                removeServerAlarm(selectedItem);
            } else {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn báo thức cần hủy.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        btnCancelAllAlarms.addActionListener(ae -> cancelAllServerAlarms());
    }

    // --- Nhật ký ---
    private void logActivity(final String message) {
        final String t = Utils.formatNowFull();
        SwingUtilities.invokeLater(() -> {
            try {
                activityModel.addRow(new Object[]{t, message});
            } catch (Exception ex) {
                System.err.println("UI log error: " + ex.getMessage());
            }
        });
        try {
            FileUtils.append("client_activity.txt", message);
        } catch (Exception ex) {
            System.err.println("File log error: " + ex.getMessage());
        }
    }

    // --- Đồng hồ cục bộ ---
    private void startLocalClock() {
        scheduler.scheduleAtFixedRate(() -> {
            final ZonedDateTime nowWithOffset = ZonedDateTime.now(syncedZoneId)
                    .plus(timeOffsetMillis, ChronoUnit.MILLIS);
            final String t = nowWithOffset.toLocalTime()
                    .withNano(0)
                    .toString();
            SwingUtilities.invokeLater(() -> {
                lblLocalClock.setText(t);
                lblAlarmClock.setText(t);
                analogClock.setTime(nowWithOffset); // Cập nhật đồng hồ kim
            });
        }, 0, 1, TimeUnit.SECONDS);
    }

    // ============================
    // Central socket listener
    // ============================
    private void startSocketListener() {
        if (socket == null) return;
        listenerThread = new Thread(() -> {
            byte[] buf = new byte[4096];
            while (!socket.isClosed() && !Thread.currentThread().isInterrupted()) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    socket.receive(p);
                    String msg = new String(p.getData(), 0, p.getLength(), "UTF-8").trim();

                    // Nếu là alarm notification — xử lý ngay
                    if (msg.startsWith("ALARM_RING:") || msg.startsWith("ALARM_TRIGGERED:") || msg.startsWith("ALARM_TRIGGER:")) {
                        handleAlarmNotification(msg);
                        // cũng thêm vào queue nếu bạn muốn ghi log hoặc cho các caller đọc (we will handle skipping ALARM in waitForResponse)
                        incomingMessages.offer(msg);
                    } else {
                        // các phản hồi cho request (TIME, PONG, CALC result, ACKs...)
                        incomingMessages.offer(msg);
                    }
                } catch (java.net.SocketTimeoutException ste) {
                    // ignore: tiếp tục vòng lặp để chờ gói tiếp theo
                } catch (Exception ex) {
                    if (!socket.isClosed()) {
                        logActivity("Lỗi listener socket: " + ex.getMessage());
                    }
                    break;
                }
            }
        }, "Client-Socket-Listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
        logActivity("Đã khởi động luồng lắng nghe socket");
    }

    private void handleAlarmNotification(String msg) {
        String timePart = msg.contains(":") ? msg.substring(msg.indexOf(':') + 1) : msg;
        final String display = timePart;
        SwingUtilities.invokeLater(() -> {
            Toolkit.getDefaultToolkit().beep();
            JOptionPane.showMessageDialog(
                    ClientGUI.this,
                    "⏰ Báo thức! " + display,
                    "Báo thức",
                    JOptionPane.INFORMATION_MESSAGE
            );
        });
        logActivity("Nhận thông báo báo thức từ server: " + msg);
    }

    /**
     * Poll incomingMessages queue for a non-alarm response until timeout.
     * If an ALARM_* message appears, it's handled immediately and skipped.
     * Returns null if timeout.
     */
    private String waitForResponse(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            long wait = deadline - System.currentTimeMillis();
            try {
                String msg = incomingMessages.poll(Math.max(1, wait), TimeUnit.MILLISECONDS);
                if (msg == null) return null;
                if (msg.startsWith("ALARM_RING:") || msg.startsWith("ALARM_TRIGGERED:") || msg.startsWith("ALARM_TRIGGER:")) {
                    // đã tự động xử lý trong listener, tiếp tục chờ response khác
                    continue;
                }
                return msg;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    // --- Đồng bộ ---
    private void syncOnce() {
        if (socket == null) {
            logActivity("Socket chưa sẵn sàng");
            return;
        }
        scheduler.execute(() -> {
            try {
                long t0 = System.currentTimeMillis();
                String requestString = "TIME_REQUEST";
                byte[] req = requestString.getBytes("UTF-8");
                InetAddress addr = InetAddress.getByName(serverHost);
                DatagramPacket p = new DatagramPacket(req, req.length, addr, serverPort);
                socket.send(p);
                logActivity("Đã gửi TIME_REQUEST tới " + serverHost + ":" + serverPort);

                String serverResponse = waitForResponse(SOCKET_TIMEOUT_MS);
                if (serverResponse == null) {
                    logActivity("Hết thời gian chờ (không có phản hồi)");
                    return;
                }

                final String resp = serverResponse;
                SwingUtilities.invokeLater(() -> lblServerTime.setText(resp));
                logActivity("Nhận thời gian từ Server: " + resp);

                // Phân tích chuỗi phản hồi để lấy thời gian và múi giờ
                String[] parts = resp.split("\\(");
                String serverTimeStr = parts[0].trim();
                String serverZoneStr = parts.length > 1 ? parts[1].replace(")", "").trim() : "UTC";

                // Lưu múi giờ mới và cập nhật nhãn trên UI
                syncedZoneId = ZoneId.of(serverZoneStr);
                SwingUtilities.invokeLater(() -> lblLocalZone.setText("Múi giờ đồng bộ: " + syncedZoneId.getId()));

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
                ZonedDateTime serverTime = LocalDateTime.parse(serverTimeStr, formatter)
                        .atZone(syncedZoneId);

                long t1 = System.currentTimeMillis(); // nhận thời gian bây giờ
                long rtt = t1 - t0;
                long correctedClientTimeMillis = t1 - rtt / 2;

                timeOffsetMillis = serverTime.toInstant().toEpochMilli() - correctedClientTimeMillis;

                logActivity(String.format("Đã đồng bộ. Độ lệch: %d ms", timeOffsetMillis));
                FileUtils.append("client_log.txt", "Đồng bộ: " + resp);
            } catch (Exception ex) {
                logActivity("Lỗi đồng bộ: " + ex.getMessage());
            }
        });
    }

    private void pingServer() {
        if (socket == null) {
            logActivity("Socket chưa sẵn sàng");
            return;
        }
        scheduler.execute(() -> {
            try {
                byte[] req = "PING".getBytes("UTF-8");
                InetAddress addr = InetAddress.getByName(serverHost);
                DatagramPacket p = new DatagramPacket(req, req.length, addr, serverPort);
                long t0 = System.currentTimeMillis();
                socket.send(p);
                logActivity("Gửi PING tới " + serverHost + ":" + serverPort);

                String r = waitForResponse(SOCKET_TIMEOUT_MS);
                long t1 = System.currentTimeMillis();
                if (r == null) {
                    logActivity("Ping timeout");
                } else {
                    logActivity("Ping phản hồi: " + r + " RTT=" + (t1 - t0) + " ms");
                }
            } catch (Exception ex) {
                logActivity("Lỗi Ping: " + ex.getMessage());
            }
        });
    }

    private void scheduleAutoSync(int seconds) {
        cancelAutoSync();
        autoSyncFuture = scheduler.scheduleAtFixedRate(() -> syncOnce(), 0, Math.max(1, seconds), TimeUnit.SECONDS);
        logActivity("Đã hẹn tự động đồng bộ mỗi " + seconds + " giây");
    }

    private void cancelAutoSync() {
        if (autoSyncFuture != null) {
            autoSyncFuture.cancel(true);
            autoSyncFuture = null;
            logActivity("Đã hủy tự động đồng bộ");
        }
    }

    // --- Đếm ngược ---
    private void startCountdown(long seconds) {
        stopCountdown();
        countdownRemaining = seconds;
        updateCountdownLabel(countdownRemaining);
        countdownFuture = scheduler.scheduleAtFixedRate(() -> {
            countdownRemaining--;
            SwingUtilities.invokeLater(() -> updateCountdownLabel(countdownRemaining));
            if (countdownRemaining <= 0) {
                stopCountdown();
                SwingUtilities.invokeLater(() -> {
                    Toolkit.getDefaultToolkit().beep();
                    JOptionPane.showMessageDialog(ClientGUI.this, "Đếm ngược kết thúc!", "Đếm ngược", JOptionPane.INFORMATION_MESSAGE);
                });
                logActivity("Đếm ngược kết thúc");
            }
        }, 1, 1, TimeUnit.SECONDS);
        logActivity("Bắt đầu đếm ngược: " + seconds + "s");
    }

    private void updateCountdownLabel(long sec) {
        if (sec < 0) sec = 0;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        lblCountdownBig.setText(String.format("%02d:%02d:%02d", h, m, s));
    }

    private void stopCountdown() {
        if (countdownFuture != null) {
            countdownFuture.cancel(true);
            countdownFuture = null;
        }
        countdownRemaining = 0;
        updateCountdownLabel(0);
        logActivity("Đã dừng đếm ngược");
    }

    // --- Báo thức (Gửi yêu cầu đến Server) ---
    private void setServerAlarm(int h, int m) {
        if (socket == null) {
            logActivity("Socket chưa sẵn sàng");
            return;
        }
        scheduler.execute(() -> {
            try {
                String alarmId = UUID.randomUUID().toString();
                String requestString = String.format("ALARM_SET:%s:%02d:%02d", alarmId, h, m);
                byte[] req = requestString.getBytes("UTF-8");
                InetAddress addr = InetAddress.getByName(serverHost);
                DatagramPacket p = new DatagramPacket(req, req.length, addr, serverPort);
                socket.send(p);
                logActivity("Đã gửi yêu cầu cài báo thức " + h + ":" + m + " đến server. ID: " + alarmId);

                String response = waitForResponse(SOCKET_TIMEOUT_MS);
                if (response == null) {
                    logActivity("Hết thời gian chờ (không có phản hồi)");
                    return;
                }
                logActivity("Phản hồi từ server: " + response);
                if (response.contains("thành công")) {
                    AlarmItem item = new AlarmItem(alarmId, String.format("%02d:%02d", h, m));
                    SwingUtilities.invokeLater(() -> alarmListModel.addElement(item));
                }
            } catch (Exception ex) {
                logActivity("Lỗi cài báo thức: " + ex.getMessage());
            }
        });
    }

    private void removeServerAlarm(AlarmItem item) {
        if (socket == null) {
            logActivity("Socket chưa sẵn sàng");
            return;
        }
        scheduler.execute(() -> {
            try {
                String requestString = "ALARM_CANCEL:" + item.id;
                byte[] req = requestString.getBytes("UTF-8");
                InetAddress addr = InetAddress.getByName(serverHost);
                DatagramPacket p = new DatagramPacket(req, req.length, addr, serverPort);
                socket.send(p);
                logActivity("Đã gửi yêu cầu hủy báo thức " + item.label + " đến server. ID: " + item.id);

                String response = waitForResponse(SOCKET_TIMEOUT_MS);
                if (response == null) {
                    logActivity("Hết thời gian chờ (không có phản hồi)");
                    return;
                }
                logActivity("Phản hồi từ server: " + response);
                if (response.contains("thành công")) {
                    SwingUtilities.invokeLater(() -> alarmListModel.removeElement(item));
                }
            } catch (Exception ex) {
                logActivity("Lỗi hủy báo thức: " + ex.getMessage());
            }
        });
    }

    private void cancelAllServerAlarms() {
        if (socket == null) {
            logActivity("Socket chưa sẵn sàng");
            return;
        }
        scheduler.execute(() -> {
            try {
                String requestString = "ALARM_CANCEL_ALL";
                byte[] req = requestString.getBytes("UTF-8");
                InetAddress addr = InetAddress.getByName(serverHost);
                DatagramPacket p = new DatagramPacket(req, req.length, addr, serverPort);
                socket.send(p);
                logActivity("Đã gửi yêu cầu hủy tất cả báo thức đến server.");

                String response = waitForResponse(SOCKET_TIMEOUT_MS);
                if (response == null) {
                    logActivity("Hết thời gian chờ (không có phản hồi)");
                    return;
                }
                logActivity("Phản hồi từ server: " + response);
                if (response.contains("thành công")) {
                    SwingUtilities.invokeLater(alarmListModel::clear);
                }
            } catch (Exception ex) {
                logActivity("Lỗi hủy tất cả báo thức: " + ex.getMessage());
            }
        });
    }

    // Shutdown
    private void shutdown() {
        try {
            cancelAutoSync();
            stopCountdown();
            if (listenerThread != null) listenerThread.interrupt();
            if (socket != null && !socket.isClosed()) socket.close();
            scheduler.shutdownNow();
            logActivity("Client tắt");
        } catch (Exception e) {
            // ignore
        }
    }

    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            // ignore
        }

        SwingUtilities.invokeLater(() -> {
            ClientGUI gui = new ClientGUI();
            gui.setVisible(true);
        });
    }

    private static class AlarmItem {
        private final String id;
        private final String label;

        public AlarmItem(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
