// File: ServerGUI.java
package btl;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * UDP Time Server - hiển thị đồng hồ số với giao diện đẹp hơn
 */
public class ServerGUI extends JFrame implements Runnable {
    public static final int PORT = 9876;

    private final DefaultTableModel tableModel = new DefaultTableModel(new String[]{"Thời gian", "Sự kiện"}, 0);
    private volatile boolean running = false;
    private DatagramSocket socket;

    // THAY ĐỔI: Khai báo JTable là một trường của lớp để có thể truy cập trong phương thức log
    private JTable table; 
    private JLabel lblDigitalClock;
    private ZoneId currentZone = ZoneId.systemDefault();
    
    // Báo thức
    private final ScheduledExecutorService alarmScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<String, AlarmData> activeAlarms = new ConcurrentHashMap<>();
    
    private static class AlarmData {
        LocalTime alarmTime;
        String clientAddress;
        int clientPort;
        
        public AlarmData(LocalTime time, String addr, int port) {
            this.alarmTime = time;
            this.clientAddress = addr;
            this.clientPort = port;
        }
    }

    public ServerGUI() {
        super("Time Server");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 650); 
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(15, 15));

        // Thiết lập giao diện Nimbus cho vẻ ngoài hiện đại
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception e) {
            // ignore
        }

        // Thiết lập màu sắc và font chữ cho toàn bộ ứng dụng
        Color primaryColor = new Color(30, 144, 255); // Dodger Blue
        Color secondaryColor = new Color(240, 248, 255); // Alice Blue (Nền)
        Color accentColor = new Color(255, 69, 0); // Red-Orange (Điểm nhấn)
        
        UIManager.put("Panel.background", secondaryColor);
        UIManager.put("Table.background", Color.WHITE);
        UIManager.put("TableHeader.background", primaryColor);
        UIManager.put("TableHeader.foreground", Color.WHITE);
        UIManager.put("TitledBorder.titleColor", primaryColor.darker());
        UIManager.put("TitledBorder.font", new Font("Segoe UI", Font.BOLD, 16));
        UIManager.put("Label.font", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("ComboBox.font", new Font("Segoe UI", Font.PLAIN, 14));

        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(secondaryColor);

        // =========================================================================
        // 1. Phần Nhật ký (Log Panel)
        // =========================================================================
        // THAY ĐỔI: Khởi tạo table là trường của lớp
        this.table = new JTable(tableModel);
        this.table.setFillsViewportHeight(true);
        this.table.getTableHeader().setReorderingAllowed(false);
        this.table.getTableHeader().setResizingAllowed(false);
        this.table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        this.table.setRowHeight(20);
        
        JScrollPane sp = new JScrollPane(this.table);
        sp.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(primaryColor.darker(), 2),
                "NHẬT KÝ HOẠT ĐỘNG (LOGS)", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 18), primaryColor.darker()));
        
        // =========================================================================
        // 2. Phần Đồng hồ và Múi giờ (Clock Panel)
        // =========================================================================
        JPanel clockPanel = new JPanel(new BorderLayout(10, 10));
        clockPanel.setBackground(Color.WHITE);
        clockPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));
        
        // Header chứa múi giờ
        JPanel clockHeader = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        clockHeader.setBackground(new Color(245, 245, 245));
        
        String[] timeZones = {"Asia/Ho_Chi_Minh", "America/New_York", "Europe/London", "Asia/Tokyo", "Australia/Sydney"};
        JComboBox<String> timeZonePicker = new JComboBox<>(timeZones);
        
        // Thiết lập múi giờ mặc định
        try {
            timeZonePicker.setSelectedItem(ZoneId.systemDefault().getId());
            currentZone = ZoneId.of((String) timeZonePicker.getSelectedItem());
        } catch (Exception e) {
            currentZone = ZoneId.of("Asia/Ho_Chi_Minh");
            timeZonePicker.setSelectedItem("Asia/Ho_Chi_Minh");
        }
        
        timeZonePicker.addActionListener(e -> {
            String selectedZone = (String) timeZonePicker.getSelectedItem();
            if (selectedZone != null) {
                currentZone = ZoneId.of(selectedZone);
                log("Đã đổi múi giờ của server sang: " + selectedZone);
                updateClock();
            }
        });
        
        JLabel lblZone = new JLabel("CHỌN MÚI GIỜ SERVER:");
        lblZone.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblZone.setForeground(primaryColor.darker());
        clockHeader.add(lblZone);
        clockHeader.add(timeZonePicker);

        // Digital Clock Display
        lblDigitalClock = new JLabel("00:00:00", SwingConstants.CENTER);
        lblDigitalClock.setFont(new Font("Monospaced", Font.BOLD, 100)); // Font to be large
        lblDigitalClock.setForeground(accentColor); 
        lblDigitalClock.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0)); // Padding

        clockPanel.add(clockHeader, BorderLayout.NORTH);
        clockPanel.add(lblDigitalClock, BorderLayout.CENTER);
        
        // Tiêu đề lớn cho Clock Panel
        TitledBorder clockBorder = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(accentColor, 2),
            "ĐỒNG HỒ THỜI GIAN HIỆN TẠI", TitledBorder.CENTER, TitledBorder.TOP,
            new Font("Segoe UI", Font.BOLD, 20), accentColor);
        clockPanel.setBorder(clockBorder);


        // =========================================================================
        // 3. Kết hợp các phần tử
        // =========================================================================
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sp, clockPanel);
        split.setDividerLocation(550); // Cho Nhật ký nhiều không gian hơn
        split.setBorder(null);

        mainPanel.add(split, BorderLayout.CENTER);
        add(mainPanel, BorderLayout.CENTER);

        // Khởi động đồng hồ và scheduler
        Timer timer = new Timer(1000, e -> updateClock());
        timer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopServer();
        }));

        new Thread(this, "Server-Thread").start();
        
        startAlarmScheduler();
        
        // Cập nhật đồng hồ lần đầu
        updateClock();
    }
    
    private void stopServer() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        alarmScheduler.shutdownNow();
    }

    private void updateClock() {
        ZonedDateTime now = ZonedDateTime.now(currentZone);
        // Định dạng HH:mm:ss
        lblDigitalClock.setText(now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))); 
    }

    private void log(final String msg) {
        final String time = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy").format(LocalDateTime.now());
        SwingUtilities.invokeLater(() -> {
            tableModel.addRow(new Object[]{time, msg});
            
            // SỬA LỖI BIÊN DỊCH: Sử dụng trường JTable đã được khai báo
            int lastRow = tableModel.getRowCount() - 1;
            if (lastRow >= 0 && table != null) {
                 // Cuộn xuống dòng cuối cùng
                 table.scrollRectToVisible(table.getCellRect(lastRow, 0, true));
            }
        });
    }

    private String calculate(double num1, String operator, double num2) {
        try {
            switch (operator) {
                case "+": return String.valueOf(num1 + num2);
                case "-": return String.valueOf(num1 - num2);
                case "*": return String.valueOf(num1 * num2);
                case "/": 
                    if (num2 == 0) {
                        return "Lỗi: Chia cho 0";
                    }
                    return String.valueOf(num1 / num2);
                default: return "Lỗi: Phép toán không hợp lệ";
            }
        } catch (Exception e) {
            return "Lỗi: " + e.getMessage();
        }
    }
    
    private void startAlarmScheduler() {
        alarmScheduler.scheduleAtFixedRate(() -> {
            LocalTime now = LocalTime.now(currentZone);
            for (Map.Entry<String, AlarmData> entry : activeAlarms.entrySet()) {
                String id = entry.getKey();
                AlarmData alarm = entry.getValue();
                // Chỉ kiểm tra Giờ và Phút
                if (now.getHour() == alarm.alarmTime.getHour() && now.getMinute() == alarm.alarmTime.getMinute()) {
                    log("BÁO THỨC KÊU TỪ CLIENT " + alarm.clientAddress + ":" + alarm.clientPort);

                    try {
                        String alertMsg = "ALARM_RING:" + alarm.alarmTime.format(DateTimeFormatter.ofPattern("HH:mm"));
                        byte[] data = alertMsg.getBytes("UTF-8");
                        DatagramPacket packet = new DatagramPacket(
                            data, data.length,
                            InetAddress.getByName(alarm.clientAddress),
                            alarm.clientPort
                        );
                        if (socket != null && !socket.isClosed()) {
                            socket.send(packet);
                        }
                        log("Đã gửi thông báo báo thức tới client " + alarm.clientAddress + ":" + alarm.clientPort);
                    } catch (Exception ex) {
                        log("Lỗi gửi thông báo báo thức: " + ex.getMessage());
                    }

                    // Sau khi báo thức kêu, tự động hủy bỏ nó
                    activeAlarms.remove(id);
                }

            }
        }, 0, 30, TimeUnit.SECONDS); // Kiểm tra mỗi 30 giây
    }

    @Override
    public void run() {
        running = true;
        log("Server đang khởi động...");
        try (DatagramSocket ds = new DatagramSocket(PORT)) {
            this.socket = ds;
            log("Server lắng nghe tại cổng " + PORT);
            byte[] buf = new byte[4096];
            while (running) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    ds.receive(p);
                    String req = new String(p.getData(), 0, p.getLength(), "UTF-8").trim();
                    InetAddress clientAddress = p.getAddress();
                    int clientPort = p.getPort();
                    String clientKey = clientAddress.getHostAddress() + ":" + clientPort;

                    String response = "UNKNOWN";

                    if (req.startsWith("TIME_REQUEST")) {
                        response = ZonedDateTime.now(currentZone)
                                .format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy")) + " (" + currentZone.getId() + ")";
                        log("Nhận yêu cầu giờ từ client " + clientKey + ". Trả về: " + response);
                    } else if (req.startsWith("ALARM_SET:")) {
                        String[] parts = req.split(":");
                        if (parts.length == 4) {
                            try {
                                String id = parts[1];
                                int hour = Integer.parseInt(parts[2]);
                                int minute = Integer.parseInt(parts[3]);
                                
                                activeAlarms.put(id, new AlarmData(LocalTime.of(hour, minute), clientAddress.getHostAddress(), clientPort));
                                response = String.format("Đã cài báo thức lúc %02d:%02d thành công.", hour, minute);
                                log("Nhận yêu cầu cài báo thức từ " + clientKey + ". ID: " + id + ". Trả về: " + response);
                            } catch (NumberFormatException nfe) {
                                response = "Lỗi: Định dạng giờ/phút không hợp lệ.";
                                log("Lỗi xử lý yêu cầu báo thức từ " + clientKey);
                            }
                        } else {
                            response = "Lỗi: Định dạng yêu cầu không hợp lệ.";
                            log("Lỗi định dạng yêu cầu: " + req);
                        }
                    } else if (req.startsWith("ALARM_CANCEL:")) {
                        String[] parts = req.split(":");
                        if (parts.length == 2) {
                            String id = parts[1];
                            if (activeAlarms.remove(id) != null) {
                                response = "Đã hủy báo thức thành công.";
                                log("Nhận yêu cầu hủy báo thức từ " + clientKey + ". ID: " + id + ". Trả về: " + response);
                            } else {
                                response = "Không tìm thấy báo thức cần hủy.";
                                log("Không tìm thấy báo thức với ID " + id + " từ " + clientKey);
                            }
                        } else {
                            response = "Lỗi: Định dạng yêu cầu hủy không hợp lệ.";
                        }
                    } else if ("ALARM_CANCEL_ALL".equalsIgnoreCase(req)) {
                        activeAlarms.clear();
                        response = "Đã hủy tất cả báo thức thành công.";
                        log("Nhận yêu cầu hủy tất cả báo thức từ " + clientKey + ". Trả về: " + response);
                    } else if (req.startsWith("CALC_REQUEST:")) {
                        String[] parts = req.split(":", 2)[1].split(",");
                        if (parts.length == 3) {
                            try {
                                double num1 = Double.parseDouble(parts[0]);
                                String operator = parts[1];
                                double num2 = Double.parseDouble(parts[2]);
                                response = calculate(num1, operator, num2);
                                log("Nhận yêu cầu tính toán: " + req + ". Trả về: " + response);
                            } catch (NumberFormatException nfe) {
                                response = "Lỗi: Dữ liệu nhập không phải là số.";
                                log("Lỗi xử lý yêu cầu tính toán: " + req);
                            }
                        } else {
                            response = "Lỗi: Định dạng yêu cầu không hợp lệ.";
                            log("Lỗi định dạng yêu cầu: " + req);
                        }
                    } else if ("PING".equalsIgnoreCase(req)) {
                        response = "PONG";
                        log("Nhận yêu cầu PING từ " + clientKey + ". Trả về PONG");
                    } else {
                        response = "Yêu cầu không xác định.";
                        log("Nhận yêu cầu không xác định: " + req);
                    }

                    byte[] resp = response.getBytes("UTF-8");
                    DatagramPacket rp = new DatagramPacket(resp, resp.length, clientAddress, clientPort);
                    ds.send(rp);
                } catch (Exception ex) {
                    if (running) {
                        log("Lỗi xử lý gói tin: " + ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log("Lỗi socket: " + e.getMessage());
        } finally {
            log("Server đã dừng.");
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
        SwingUtilities.invokeLater(() -> new ServerGUI().setVisible(true));
    }
}