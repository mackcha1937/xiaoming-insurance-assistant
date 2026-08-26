package com.embabel.insurance.xiaoming.service;

import com.embabel.insurance.xiaoming.api.ReadinessData;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

@Service
public class FoundationReadinessService {

    private final DataSource dataSource;

    public FoundationReadinessService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public ReadinessData check() {
        try (Connection connection = dataSource.getConnection()) {
            boolean databaseReady = connection.isValid(1);
            return new ReadinessData(
                    databaseReady ? "UP" : "DOWN",
                    Map.of("api", "UP", "database", databaseReady ? "UP" : "DOWN")
            );
        } catch (SQLException exception) {
            return new ReadinessData("DOWN", Map.of("api", "UP", "database", "DOWN"));
        }
    }
}
