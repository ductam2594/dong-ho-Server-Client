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

## Mục lục
- [1. Giới thiệu hệ thống](#1-giới-thiệu-hệ-thống)
- [2. Ngôn ngữ & Công nghệ chính](#2-ngôn-ngữ--công-nghệ-chính)
- [3. Hình ảnh các chức năng](#3-hình-ảnh-các-chức-năng)
- [4. Kiến trúc & Cấu trúc mã nguồn](#4-kiến-trúc--cấu-trúc-mã-nguồn)
- [5. Cơ sở dữ liệu](#5-cơ-sở-dữ-liệu)
- [6. Giao thức & Lệnh server](#6-giao-thức--lệnh-server)
- [7. Phân quyền](#7-phân-quyền)
- [8. Các bước cài đặt](#8-các-bước-cài-đặt)
- [9. Hướng dẫn sử dụng nhanh](#9-hướng-dẫn-sử-dụng-nhanh)
- [10. Khắc phục sự cố](#10-khắc-phục-sự-cố)
- [11. Ghi chú triển khai & bảo mật](#11-ghi-chú-triển-khai--bảo-mật)
- [12. Tài khoản mẫu](#12-tài-khoản-mẫu)
- [13. Đóng góp](#13-đóng-góp)


# Hệ thống Đồng bộ Thời gian (Server – Client)

Ứng dụng Java Swing mô phỏng việc **đồng bộ thời gian** giữa **Server** và **Client** thông qua giao thức TCP Socket.  
Hệ thống gồm 2 chương trình: **Time Server** và **Time Client**, kèm các tiện ích hỗ trợ.

## 1. Giới thiệu hệ thống

Server đóng vai trò cung cấp thời gian chuẩn, tiếp nhận kết nối và phản hồi yêu cầu từ nhiều Client.  

Client kết nối tới Server để lấy thời gian và đồng bộ đồng hồ cục bộ.  

Hệ thống đồng thời ghi log hoạt động vào file lưu trữ trên Server

## Mục tiêu
- Minh họa cơ chế trao đổi dữ liệu qua mạng  
- Quản lý nhiều kết nối  
- Đồng bộ dữ liệu  
- Ghi nhận log  

## 2. Ngôn ngữ & Công nghệ chính

