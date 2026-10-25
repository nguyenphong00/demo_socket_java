# Demo Socket Server Java + MySQL

Server lắng nghe TCP, nhận **mỗi JSON trên một dòng UTF-8**, xử lý nhiều client bằng nhóm thread có giới hạn và ghi dữ liệu vào MySQL. Mỗi dòng hợp lệ được trả về một dòng JSON xác nhận sau khi `INSERT` thành công.

## Kế hoạch và phạm vi

1. Định nghĩa giao thức truyền dữ liệu, xác thực JSON và phản hồi lỗi.
2. Xây dựng TCP server đa luồng với giới hạn số kết nối và thời gian chờ.
3. Ghi bản tin vào MySQL bằng `PreparedStatement`.
4. Cung cấp client gửi dữ liệu định kỳ, kiểm thử nhiều client và cách chạy bằng Docker Compose.

## Yêu cầu

- Docker Desktop và Docker Compose để chạy toàn bộ demo; hoặc Java 21, Maven 3.9+ và MySQL 8.4 để chạy thủ công.
- Cổng `9090` và `3306` trên máy chưa được sử dụng. Trong Compose, hai cổng chỉ mở trên `127.0.0.1`.

## Chạy nhanh bằng Docker

Tại thư mục này:

```bash
docker compose up --build -d
docker compose logs -f server
```

Khi thấy `TCP server listening on port 9090`, mở terminal khác và chạy client mô phỏng **3 kết nối**, mỗi kết nối gửi **5 bản tin**, cách nhau **1 giây**:

```bash
docker compose run --rm --no-deps server client server 9090 3 5
```

Kiểm tra dữ liệu đã lưu:

```bash
docker compose exec db mysql -usocket_user -psocket_pass socket_demo -e "SELECT id, client_id, metric, metric_value, recorded_at, received_at FROM measurements ORDER BY id DESC LIMIT 15;"
```

Kỳ vọng: client nhận 15 phản hồi `{"status":"ok","id":...}` và bảng có 15 dòng mới. Chạy lại client sẽ thêm 15 dòng nữa. Dừng dịch vụ bằng `docker compose down`; dùng `docker compose down -v` nếu muốn xóa luôn dữ liệu demo.

## Giao thức TCP

Gửi mỗi JSON kết thúc bằng `\n`. Một kết nối có thể gửi nhiều dòng. Ví dụ:

```json
{"clientId":"sensor-01","metric":"temperature","value":23.5,"timestamp":"2026-10-25T08:00:00Z"}
```

- `clientId`: chuỗi 1–64 ký tự.
- `metric`: chuỗi 1–64 ký tự.
- `value`: số JSON hữu hạn.
- `timestamp`: thời điểm ISO-8601 UTC, ví dụ `2026-10-25T08:00:00Z`.
- Mỗi dòng tối đa 16.384 ký tự; một kết nối không gửi dữ liệu trong 120 giây sẽ bị đóng.

Phản hồi thành công: `{"status":"ok","id":1}`. JSON sai hoặc thiếu trường trả về `{"status":"error","message":"..."}` và kết nối vẫn nhận tiếp bản tin khác. Khi DB lỗi, server trả `database unavailable` và không báo thành công. Khi server quá tải, kết nối mới nhận `server busy` rồi bị đóng.

Có thể thử trực tiếp qua terminal:

```bash
printf '%s\n' '{"clientId":"sensor-manual","metric":"humidity","value":55.2,"timestamp":"2026-10-25T08:00:00Z"}' | nc 127.0.0.1 9090
```

## Chạy bằng Java trên máy

Khởi tạo MySQL database `socket_demo` và user có quyền tạo bảng/ghi dữ liệu, rồi đặt các biến môi trường `DB_URL`, `DB_USER`, `DB_PASSWORD`. Server tự tạo bảng `measurements` khi khởi động.

```bash
mvn test package
DB_URL='jdbc:mysql://localhost:3306/socket_demo?useSSL=false&allowPublicKeyRetrieval=true' DB_USER=socket_user DB_PASSWORD=socket_pass java -jar target/socket-server-1.0.0.jar server
```

Terminal khác:

```bash
java -jar target/socket-server-1.0.0.jar client 127.0.0.1 9090 3 5
```

Các biến tùy chọn: `PORT` (mặc định `9090`), `MAX_CLIENTS` (mặc định `32`). Mỗi client chiếm một worker khi duy trì kết nối; hàng đợi tối đa bằng `MAX_CLIENTS`.

## Cấu trúc

- `TcpServer`: lắng nghe TCP, đọc dòng, quản lý worker và gửi phản hồi.
- `Measurement`: xác thực dữ liệu JSON.
- `MysqlMeasurementRepository`: tạo bảng và ghi MySQL.
- `DemoClient`: phát bản tin định kỳ từ nhiều client.
- `TcpServerTest`: thử nhiều kết nối đồng thời và phản hồi JSON không hợp lệ.

Đây là demo học tập. Thông tin đăng nhập trong `compose.yaml` chỉ dành cho môi trường local; khi triển khai thật cần thay mật khẩu, thêm xác thực client/TLS và cân nhắc pool kết nối DB.
