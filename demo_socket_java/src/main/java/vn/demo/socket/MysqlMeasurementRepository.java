package vn.demo.socket;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

public final class MysqlMeasurementRepository implements MeasurementRepository {
    private final String url;
    private final String user;
    private final String password;

    public MysqlMeasurementRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public void initialize() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS measurements (
              id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
              client_id VARCHAR(64) NOT NULL,
              metric VARCHAR(64) NOT NULL,
              metric_value DOUBLE NOT NULL,
              recorded_at TIMESTAMP(3) NOT NULL,
              received_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
              INDEX idx_client_recorded (client_id, recorded_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @Override
    public long save(Measurement measurement) throws SQLException {
        String sql = "INSERT INTO measurements (client_id, metric, metric_value, recorded_at) VALUES (?, ?, ?, ?)";
        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, measurement.clientId());
            statement.setString(2, measurement.metric());
            statement.setDouble(3, measurement.value());
            statement.setTimestamp(4, Timestamp.from(measurement.timestamp()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Không nhận được ID sau khi ghi dữ liệu");
                }
                return keys.getLong(1);
            }
        }
    }
}
