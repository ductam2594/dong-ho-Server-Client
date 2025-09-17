package btl;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * UDP Time Server - package btl
 * Listens on PORT and responds to "TIME_REQUEST" with server time (formatted).
 * Shows log in JTable.
 */
public class ServerGUI extends JFrame implements Runnable {
    public static final int PORT = 9876;

    private final DefaultTableModel tableModel = new DefaultTableModel(new String[]{"Time", "Event"}, 0);
    private volatile boolean running = false;
    private DatagramSocket socket;

    public ServerGUI() {
        super("UDP Time Server (btl)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(720, 420);
        setLocationRelativeTo(null);

        JTable table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createTitledBorder("Server Log"));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Listening UDP port: " + PORT));

        add(top, BorderLayout.NORTH);
        add(sp, BorderLayout.CENTER);

        // Start server thread
        new Thread(this, "Server-Thread").start();
    }

    private void log(final String msg) {
        final String time = Utils.formatNowFull();
        // ensure UI update happens on EDT
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                tableModel.addRow(new Object[]{time, msg});
            }
        });
        FileUtils.append("server_log.txt", msg);
    }

    @Override
    public void run() {
        running = true;
        log("Server starting...");
        try (DatagramSocket ds = new DatagramSocket(PORT)) {
            this.socket = ds;
            log("Server started on port " + PORT);
            byte[] buf = new byte[4096];
            while (running) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    ds.receive(p);
                    String req = new String(p.getData(), 0, p.getLength(), "UTF-8").trim();
                    String client = p.getAddress().getHostAddress() + ":" + p.getPort();
                    log("Received from " + client + " -> " + req);

                    if ("TIME_REQUEST".equalsIgnoreCase(req)) {
                        String time = Utils.formatNowFull();
                        byte[] resp = time.getBytes("UTF-8");
                        DatagramPacket rp = new DatagramPacket(resp, resp.length, p.getAddress(), p.getPort());
                        ds.send(rp);
                        log("Sent time to " + client + " -> " + time);
                    } else if ("PING".equalsIgnoreCase(req)) {
                        String pong = "PONG";
                        byte[] resp = pong.getBytes("UTF-8");
                        DatagramPacket rp = new DatagramPacket(resp, resp.length, p.getAddress(), p.getPort());
                        ds.send(rp);
                        log("Replied PONG to " + client);
                    } else {
                        String unknown = "UNKNOWN";
                        byte[] resp = unknown.getBytes("UTF-8");
                        DatagramPacket rp = new DatagramPacket(resp, resp.length, p.getAddress(), p.getPort());
                        ds.send(rp);
                        log("Replied UNKNOWN to " + client);
                    }
                } catch (Exception ex) {
                    log("Packet handling error: " + ex.getMessage());
                }
            }
        } catch (Exception e) {
            log("Socket error: " + e.getMessage());
        } finally {
            log("Server stopped.");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                ServerGUI gui = new ServerGUI();
                gui.setVisible(true);
            }
        });
    }
}
