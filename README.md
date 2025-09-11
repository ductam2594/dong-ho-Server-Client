<h2 align="center">
    <a href="https://dainam.edu.vn/vi/khoa-cong-nghe-thong-tin">
    🎓 Faculty of Information Technology (DaiNam University)
    </a>
</h2>
<h2 align="center">
    Đồng hồ Server – Client (đồng bộ thời gian)
</h2>
<div align="center">
    <p align="center">
        <img alt="AIoTLab Logo" width="170" src="https://github.com/user-attachments/assets/711a2cd8-7eb4-4dae-9d90-12c0a0a208a2" />
        <img alt="AIoTLab Logo" width="180" src="https://github.com/user-attachments/assets/dc2ef2b8-9a70-4cfa-9b4b-f6c2f25f1660" />
        <img alt="DaiNam University Logo" width="200" src="https://github.com/user-attachments/assets/77fe0fd1-2e55-4032-be3c-b1a705a1b574" />
    </p>

[![AIoTLab](https://img.shields.io/badge/AIoTLab-green?style=for-the-badge)](https://www.facebook.com/DNUAIoTLab)
[![Faculty of Information Technology](https://img.shields.io/badge/Faculty%20of%20Information%20Technology-blue?style=for-the-badge)](https://dainam.edu.vn/vi/khoa-cong-nghe-thong-tin)
[![DaiNam University](https://img.shields.io/badge/DaiNam%20University-orange?style=for-the-badge)](https://dainam.edu.vn)

</div>

# ⏰ Ứng dụng Đồng hồ Server – Client (Đồng bộ thời gian)

## 1. Giới thiệu hệ thống
Hệ thống **Đồng hồ Server – Client** cho phép nhiều máy Client đồng bộ thời gian với Server thông qua **TCP Socket**.  
- **Server**: quản lý, hiển thị đồng hồ thời gian thực, trả lời yêu cầu đồng bộ từ Client.  
- **Client**: hiển thị đồng hồ cá nhân, có thể đồng bộ thủ công hoặc tự động với Server.  
- **Log**: mọi hoạt động đồng bộ được ghi vào file (server_log.txt, client_log.txt).  

---

## 2. Ngôn ngữ & Công nghệ chính
- **Java SE 8+**  
- **Java Swing** (giao diện người dùng)  
- **TCP Socket** (ServerSocket, Socket)  
- **Đa luồng**: xử lý nhiều client kết nối đồng thời.  
- **File I/O**: lưu log đồng bộ vào file.  

---

## 3. Hình ảnh các chức năng
### Server GUI
- Hiển thị đồng hồ thời gian thực.  
- Danh sách client kết nối.  
- Nút Start/Stop server.  

![Server](https://via.placeholder.com/600x300.png?text=Server+GUI)

### Client GUI
- Hiển thị đồng hồ cá nhân.  
- Nhập IP server để kết nối.  
- Nút đồng bộ, tùy chọn tự động 30s.  

![Client](https://via.placeholder.com/600x300.png?text=Client+GUI)

---

## 4. Kiến trúc & Cấu trúc mã nguồn

**Luồng hoạt động:**
1. Client kết nối tới Server qua cổng 5000 (TCP).  
2. Client gửi lệnh `"TIME"`.  
3. Server trả về thời gian hiện tại.  
4. Cả Server và Client ghi log vào file.  

---

## 5. Cơ sở dữ liệu
Hệ thống sử dụng **file text** để lưu log:  
- `server_log.txt`: lưu hoạt động đồng bộ phía server.  
- `client_log.txt`: lưu hoạt động đồng bộ phía client.  

Ví dụ log:  

---

## 6. Giao thức & Lệnh server
- **Giao thức**: TCP (Transmission Control Protocol).  
- **Cổng mặc định**: `5000`.  
- **Lệnh hỗ trợ**:  
  - `TIME` → Client gửi để yêu cầu đồng bộ.  
  - Server trả về chuỗi thời gian dạng `HH:mm:ss`.  

---

## 📖 Ý nghĩa
- Giúp sinh viên hiểu rõ cơ chế **Client-Server** trong lập trình mạng.  
- Thực hành kết hợp **Socket + GUI + File I/O**.  
- Ứng dụng có thể mở rộng để đồng bộ nhiều loại dữ liệu khác, không chỉ thời gian.
