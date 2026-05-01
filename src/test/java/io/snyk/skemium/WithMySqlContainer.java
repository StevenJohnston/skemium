package io.snyk.skemium;

import io.debezium.config.Configuration;
import io.debezium.relational.RelationalDatabaseConnectorConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/// Base class for tests that require a MySQL database.
///
/// This class starts a [MySQLContainer] (using [BeforeAll] and [AfterAll])
/// with a test fixture DB and stops it after the tests are done.
/// It also provides a [MySQLContainer] instance and a [Configuration] useful to connect to the DB.
///
/// @see [MySQLContainer]
public abstract class WithMySqlContainer {
    protected static MySQLContainer<?> MYSQL_CONTAINER = initMySqlContainer();

    @BeforeAll
    static void startDB() {
        MYSQL_CONTAINER.start();
    }

    @AfterAll
    static void stopDB() {
        MYSQL_CONTAINER.stop();
    }

    protected static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                MYSQL_CONTAINER.getJdbcUrl(),
                MYSQL_CONTAINER.getUsername(),
                MYSQL_CONTAINER.getPassword()
        );
    }

    protected static final String MYSQL_IMG = "mysql";
    protected static final String MYSQL_VER = "8.0.33";
    protected static final String MYSQL_IMGVER = MYSQL_IMG + ":" + MYSQL_VER;

    protected static final int MYSQL_DEFAULT_PORT = 3306;

    protected static final String INITDB_SCRIPT = "db_schema/chinook.mysql.sql";
    protected static final String DB_NAME = "chinook";
    protected static final String DB_USER = "chinook-db-user";
    protected static final String DB_PASS = "chinook-db-pass";

    static MySQLContainer<?> initMySqlContainer() {
        return new MySQLContainer<>(MYSQL_IMGVER)
                .withInitScript(INITDB_SCRIPT)
                .withDatabaseName(DB_NAME)
                .withUsername(DB_USER)
                .withPassword(DB_PASS);
    }

    protected static Configuration createMySqlContainerConfiguration(final MySQLContainer<?> container) {
        return Configuration.create()
                .with(RelationalDatabaseConnectorConfig.HOSTNAME, container.getHost())
                .with(RelationalDatabaseConnectorConfig.PORT, container.getMappedPort(MYSQL_DEFAULT_PORT).toString())
                .with(RelationalDatabaseConnectorConfig.USER, DB_USER)
                .with(RelationalDatabaseConnectorConfig.PASSWORD, DB_PASS)
                .with(RelationalDatabaseConnectorConfig.DATABASE_NAME, DB_NAME)
                .with(RelationalDatabaseConnectorConfig.TOPIC_PREFIX, "test-topic-prefix")
                .build();
    }
}
