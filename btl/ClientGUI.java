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
import java.time.LocalTime;
import java.util.Calendar;
import java.util.concurrent.*;

/**
 * UDP Time Client GUI (btl)
 * - Tabbed UI: Sync | Countdown | Alarm | Activity Log
 * - Uses DatagramSocket for TIME_REQUEST and PING
 */
public class ClientGUI extends JFrame {
    private DatagramSocket socket = null;
    private String serverHost = "localhost";
    private int serverPort = 9876;
    private final int SOCKET_TIMEOUT_MS = 3000;

    // UI components
    private final JLabel lblLocalClock = new JLabel("--:--:--", SwingConstants.CENTER);
    private final JLabel lblServerTime = new JLabel("Not synced", SwingConstants.CENTER);

    // activity table model
    private final DefaultTableModel activityModel = new DefaultTableModel(new String[]{"Time", "Event"}, 0);

    // scheduling
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private ScheduledFuture<?> autoSyncFuture;

    // countdown
    private ScheduledFuture<?> countdownFuture;
    private volatile long countdownRemaining = 0;
    private final JLabel lblCountdownBig = new JLabel("00:00", SwingConstants.CENTER);

    // alarm
    private ScheduledFuture<?> alarmChecker;
    private volatile boolean alarmActive = false;
    private volatile int alarmHour = -1, alarmMinute = -1;
    private final JLabel lblAlarmStatus = new JLabel("No alarm set", SwingConstants.CENTER);

    public ClientGUI() {
        super("UDP Time Client (btl)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 620);
        setLocationRelativeTo(null);

        initSocket();
        initUI();
        startLocalClock();

        // Ensure resources cleaned up
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
            logActivity("UDP socket created on local port " + socket.getLocalPort());
        } catch (Exception e) {
            logActivity("Socket init error: " + e.getMessage());
            socket = null;
        }
    }

    private void initUI() {
        JPanel main = new JPanel(new BorderLayout(8,8));
        main.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        // Top: two cards
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6,6,6,6);
        gbc.fill = GridBagConstraints.BOTH;

        // Local clock card
        JPanel localCard = new JPanel(new BorderLayout());
        localCard.setBorder(new TitledBorder("Local Clock"));
        lblLocalClock.setFont(new Font("Monospaced", Font.BOLD, 36));
        localCard.add(lblLocalClock, BorderLayout.CENTER);
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.4; gbc.weighty = 0.3;
        top.add(localCard, gbc);

        // Server card
        JPanel serverCard = new JPanel(new BorderLayout());
        serverCard.setBorder(new TitledBorder("Server Time / Sync"));
        lblServerTime.setFont(new Font("Monospaced", Font.BOLD, 28));
        serverCard.add(lblServerTime, BorderLayout.CENTER);

        JPanel syncControls = new JPanel(new FlowLayout(FlowLayout.CENTER, 8,8));
        final JTextField tfHost = new JTextField(serverHost, 14);
        final JSpinner spnPort = new JSpinner(new SpinnerNumberModel(serverPort, 1024, 65535, 1));
        JButton btnSyncNow = new JButton("Sync Now");
        JButton btnPing = new JButton("Ping");
        final JCheckBox chkAuto = new JCheckBox("Auto-sync every");
        final JSpinner spnAutoSec = new JSpinner(new SpinnerNumberModel(10, 2, 3600, 1));

        syncControls.add(new JLabel("Host:")); syncControls.add(tfHost);
        syncControls.add(new JLabel("Port:")); syncControls.add(spnPort);
        syncControls.add(btnSyncNow); syncControls.add(btnPing);
        syncControls.add(chkAuto); syncControls.add(spnAutoSec); syncControls.add(new JLabel("s"));

        serverCard.add(syncControls, BorderLayout.SOUTH);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.6; gbc.weighty = 0.3;
        top.add(serverCard, gbc);

        main.add(top, BorderLayout.NORTH);

        // Center: tabbed pane
        JTabbedPane tabs = new JTabbedPane();

        // Countdown tab
        JPanel countdownPanel = new JPanel(new BorderLayout(8,8));
        JPanel cntTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        final JTextField tfSeconds = new JTextField("60", 8);
        JButton btnCntStart = new JButton("Start");
        JButton btnCntStop = new JButton("Stop");
        cntTop.add(new JLabel("Seconds:")); cntTop.add(tfSeconds); cntTop.add(btnCntStart); cntTop.add(btnCntStop);
        lblCountdownBig.setFont(new Font("Monospaced", Font.BOLD, 48));
        countdownPanel.add(cntTop, BorderLayout.NORTH);
        countdownPanel.add(lblCountdownBig, BorderLayout.CENTER);
        tabs.addTab("Countdown", countdownPanel);

        // Alarm tab
        JPanel alarmPanel = new JPanel(new BorderLayout(8,8));
        JPanel alTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        final JSpinner spnHour = new JSpinner(new SpinnerNumberModel(LocalTime.now().getHour(), 0, 23, 1));
        final JSpinner spnMin = new JSpinner(new SpinnerNumberModel(LocalTime.now().getMinute(), 0, 59, 1));
        JButton btnSetAlarm = new JButton("Set Alarm");
        JButton btnCancelAlarm = new JButton("Cancel Alarm");
        alTop.add(new JLabel("Hour:")); alTop.add(spnHour); alTop.add(new JLabel("Min:")); alTop.add(spnMin);
        alTop.add(btnSetAlarm); alTop.add(btnCancelAlarm);
        alarmPanel.add(alTop, BorderLayout.NORTH);
        alarmPanel.add(lblAlarmStatus, BorderLayout.CENTER);
        tabs.addTab("Alarm", alarmPanel);

        // Activity log tab
        JTable activityTable = new JTable(activityModel);
        activityTable.setFillsViewportHeight(true);
        JScrollPane spActivity = new JScrollPane(activityTable);
        spActivity.setBorder(new TitledBorder("Activity Log"));
        tabs.addTab("Activity Log", spActivity);

        main.add(tabs, BorderLayout.CENTER);

        add(main);

        // Actions
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
                long s = Long.parseLong(tfSeconds.getText().trim());
                startCountdown(s);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Enter valid seconds");
            }
        });

        btnCntStop.addActionListener(ae -> stopCountdown());

        btnSetAlarm.addActionListener(ae -> {
            int h = (Integer) spnHour.getValue();
            int m = (Integer) spnMin.getValue();
            setAlarm(h, m);
        });

        btnCancelAlarm.addActionListener(ae -> cancelAlarm());
    }

    // --- Logging ---
    private void logActivity(final String message) {
        final String t = Utils.formatNowFull(); // final local for safe capture
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    activityModel.addRow(new Object[]{t, message});
                } catch (Exception ex) {
                    System.err.println("UI log error: " + ex.getMessage());
                }
            }
        });
        try {
            FileUtils.append("client_activity.txt", message);
        } catch (Exception ex) {
            System.err.println("File log error: " + ex.getMessage());
        }
    }

    // --- Local clock ---
    private void startLocalClock() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                final String t = Utils.formatNowTimeOnly();
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        lblLocalClock.setText(t);
                    }
                });
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    // --- Sync ---
    private void syncOnce() {
        if (socket == null) {
            logActivity("Socket not ready");
            return;
        }
        scheduler.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] req = "TIME_REQUEST".getBytes("UTF-8");
                    InetAddress addr = InetAddress.getByName(serverHost);
                    DatagramPacket p = new DatagramPacket(req, req.length, addr, serverPort);
                    socket.send(p);
                    logActivity("Sent TIME_REQUEST to " + serverHost + ":" + serverPort);

                    byte[] buf = new byte[4096];
                    DatagramPacket rp = new DatagramPacket(buf, buf.length);
                    socket.receive(rp);
                    final String serverTime = new String(rp.getData(), 0, rp.getLength(), "UTF-8");
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            lblServerTime.setText(serverTime);
                        }
                    });
                    logActivity("Received server time: " + serverTime);
                    FileUtils.append("client_log.txt", "Synced: " + serverTime);
                } catch (java.net.SocketTimeoutException ste) {
                    logActivity("Sync timeout (no response)");
                } catch (Exception ex) {
                    logActivity("Sync error: " + ex.getMessage());
                }
            }
        });
    }

    private void pingServer() {
        if (socket == null) {
            logActivity("Socket not ready");
            return;
        }
        scheduler.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] req = "PING".getBytes("UTF-8");
                    InetAddress addr = InetAddress.getByName(serverHost);
                    DatagramPacket p = new DatagramPacket(req, req.length, addr, serverPort);
                    long t0 = System.currentTimeMillis();
                    socket.send(p);

                    byte[] buf = new byte[1024];
                    DatagramPacket rp = new DatagramPacket(buf, buf.length);
                    socket.receive(rp);
                    long t1 = System.currentTimeMillis();
                    String r = new String(rp.getData(), 0, rp.getLength(), "UTF-8");
                    logActivity("Ping reply: " + r + " RTT=" + (t1 - t0) + " ms");
                } catch (java.net.SocketTimeoutException ste) {
                    logActivity("Ping timeout");
                } catch (Exception ex) {
                    logActivity("Ping error: " + ex.getMessage());
                }
            }
        });
    }

    private void scheduleAutoSync(int seconds) {
        cancelAutoSync();
        autoSyncFuture = scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                syncOnce();
            }
        }, 0, Math.max(1, seconds), TimeUnit.SECONDS);
        logActivity("Auto-sync scheduled every " + seconds + "s");
    }

    private void cancelAutoSync() {
        if (autoSyncFuture != null) {
            autoSyncFuture.cancel(true);
            autoSyncFuture = null;
            logActivity("Auto-sync canceled");
        }
    }

    // --- Countdown ---
    private void startCountdown(long seconds) {
        stopCountdown();
        countdownRemaining = seconds;
        updateCountdownLabel(countdownRemaining);
        countdownFuture = scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                countdownRemaining--;
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        updateCountdownLabel(countdownRemaining);
                    }
                });
                if (countdownRemaining <= 0) {
                    stopCountdown();
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            Toolkit.getDefaultToolkit().beep();
                            JOptionPane.showMessageDialog(ClientGUI.this, "Countdown finished!", "Countdown", JOptionPane.INFORMATION_MESSAGE);
                        }
                    });
                    logActivity("Countdown finished");
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
        logActivity("Countdown started: " + seconds + "s");
    }

    private void updateCountdownLabel(long sec) {
        if (sec < 0) sec = 0;
        long m = sec / 60;
        long s = sec % 60;
        lblCountdownBig.setText(String.format("%02d:%02d", m, s));
    }

    private void stopCountdown() {
        if (countdownFuture != null) {
            countdownFuture.cancel(true);
            countdownFuture = null;
        }
        countdownRemaining = 0;
        updateCountdownLabel(0);
        logActivity("Countdown stopped");
    }

    // --- Alarm ---
    private void setAlarm(int h, int m) {
        cancelAlarm();
        alarmHour = h;
        alarmMinute = m;
        alarmActive = true;
        final String status = String.format("Alarm set for %02d:%02d", h, m);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                lblAlarmStatus.setText(status);
            }
        });
        logActivity(status);
        alarmChecker = scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                LocalTime now = LocalTime.now();
                if (alarmActive && now.getHour() == alarmHour && now.getMinute() == alarmMinute) {
                    alarmActive = false;
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            Toolkit.getDefaultToolkit().beep();
                            JOptionPane.showMessageDialog(ClientGUI.this, "Alarm! " + String.format("%02d:%02d", alarmHour, alarmMinute), "Alarm", JOptionPane.INFORMATION_MESSAGE);
                            lblAlarmStatus.setText("No alarm set");
                        }
                    });
                    logActivity("Alarm triggered at " + String.format("%02d:%02d", alarmHour, alarmMinute));
                    cancelAlarm();
                }
            }
        }, 0, 30, TimeUnit.SECONDS);
    }

    private void cancelAlarm() {
        if (alarmChecker != null) {
            alarmChecker.cancel(true);
            alarmChecker = null;
        }
        alarmActive = false;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                lblAlarmStatus.setText("No alarm set");
            }
        });
        logActivity("Alarm canceled");
    }

    // Shutdown resources
    private void shutdown() {
        try {
            cancelAutoSync();
            stopCountdown();
            cancelAlarm();
            if (socket != null && !socket.isClosed()) socket.close();
            scheduler.shutdownNow();
            logActivity("Client shutdown");
        } catch (Exception e) {
            // ignore
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                ClientGUI gui = new ClientGUI();
                gui.setVisible(true);
            }
        });
    }
}
