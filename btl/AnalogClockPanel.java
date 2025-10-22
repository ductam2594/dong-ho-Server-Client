package btl;

import javax.swing.*;
import java.awt.*;
import java.time.ZonedDateTime;

public class AnalogClockPanel extends JPanel {

    private ZonedDateTime time;

    public AnalogClockPanel() {
        this.time = ZonedDateTime.now();
        setPreferredSize(new Dimension(200, 200));
    }

    public void setTime(ZonedDateTime time) {
        this.time = time;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(getWidth() / 2, getHeight() / 2);

        int r = Math.min(getWidth(), getHeight()) / 2 - 10;
        int cx = 0;
        int cy = 0;

        // Vòng tròn
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2));
        g2.drawOval(-r, -r, 2 * r, 2 * r);

        // Đánh dấu phút
        g2.setColor(Color.GRAY);
        for (int i = 0; i < 60; i++) {
            double angle = Math.toRadians(i * 6);
            int x1 = (int) (cx + (r - 5) * Math.sin(angle));
            int y1 = (int) (cy - (r - 5) * Math.cos(angle));
            int x2 = (int) (cx + r * Math.sin(angle));
            int y2 = (int) (cy - r * Math.cos(angle));
            g2.drawLine(x1, y1, x2, y2);
        }

        // Đánh dấu giờ
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(4));
        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i * 30);
            int x1 = (int) (cx + (r - 15) * Math.sin(angle));
            int y1 = (int) (cy - (r - 15) * Math.cos(angle));
            int x2 = (int) (cx + r * Math.sin(angle));
            int y2 = (int) (cy - r * Math.cos(angle));
            g2.drawLine(x1, y1, x2, y2);
        }
        
        // Vẽ số giờ
        g2.setFont(new Font("Arial", Font.BOLD, 14));
        for (int i = 1; i <= 12; i++) {
            String hourText = String.valueOf(i);
            FontMetrics fm = g2.getFontMetrics();
            int stringWidth = fm.stringWidth(hourText);
            int stringHeight = fm.getAscent();

            double angle = Math.toRadians(i * 30);
            int numberX = (int) (cx + (r - 40) * Math.sin(angle)) - stringWidth / 2;
            int numberY = (int) (cy - (r - 40) * Math.cos(angle)) + stringHeight / 2;
            g2.drawString(hourText, numberX, numberY);
        }

        int hour = time.getHour();
        int minute = time.getMinute();
        int second = time.getSecond();

        double angleHour = Math.toRadians((hour % 12 + minute / 60.0) * 30);
        double angleMinute = Math.toRadians(minute * 6);
        double angleSecond = Math.toRadians(second * 6);

        // Kim giờ
        g2.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(25, 25, 112));
        g2.drawLine(cx, cy,
                (int) (cx + (r - 70) * Math.sin(angleHour)),
                (int) (cy - (r - 70) * Math.cos(angleHour)));

        // Kim phút
        g2.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(65, 105, 225));
        g2.drawLine(cx, cy,
                (int) (cx + (r - 40) * Math.sin(angleMinute)),
                (int) (cy - (r - 40) * Math.cos(angleMinute)));

        // Kim giây
        g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(220, 20, 60));
        g2.drawLine(cx, cy,
                (int) (cx + (r - 30) * Math.sin(angleSecond)),
                (int) (cy - (r - 30) * Math.cos(angleSecond)));

        // Tâm đồng hồ
        g2.setColor(Color.WHITE);
        g2.fillOval(-5, -5, 10, 10);
        g2.setColor(Color.BLACK);
        g2.drawOval(-5, -5, 10, 10);
    }
}