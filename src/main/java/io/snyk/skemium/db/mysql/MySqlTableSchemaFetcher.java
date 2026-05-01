package io.snyk.skemium.db.mysql;

import io.debezium.config.Configuration;
import io.debezium.connector.binlog.jdbc.BinlogFieldReader;
import io.debezium.connector.mysql.MySqlConnectorConfig;
import io.debezium.connector.mysql.MySqlDatabaseSchema;
import io.debezium.connector.mysql.jdbc.MySqlConnection;
import io.debezium.connector.mysql.jdbc.MySqlConnectionConfiguration;
import io.debezium.connector.mysql.jdbc.MySqlFieldReaderResolver;
import io.debezium.connector.mysql.jdbc.MySqlValueConverters;
import io.debezium.relational.TableId;
import io.debezium.relational.TableSchema;
import io.debezium.relational.Tables;
import io.debezium.schema.SchemaNameAdjuster;
import io.snyk.skemium.db.CatalogSchemaAndTableTopicNamingStrategy;
import io.snyk.skemium.db.TableSchemaFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static io.debezium.relational.RelationalDatabaseConnectorConfig.COLUMN_EXCLUDE_LIST;

/**
 * The {@link TableSchemaFetcher} for MySQL.
 * <p>
 * {@see https://www.mysql.com/}
 */
public class MySqlTableSchemaFetcher implements TableSchemaFetcher {
    private static final Logger LOG = LoggerFactory.getLogger(MySqlTableSchemaFetcher.class);

    private final Configuration configuration;
    private final MySqlConnection connection;
    private final MySqlValueConverters valueConverter;

    private final Set<String> mysqlBuiltInDatabases = Set.of(
            "information_schema",
            "mysql",
            "performance_schema",
            "sys"
    );

    public MySqlTableSchemaFetcher(final Configuration config) throws RuntimeException {
        this.configuration = config.edit()
                .with("schema.history.internal", "io.debezium.relational.history.MemorySchemaHistory")
                .build();

        LOG.trace("Creating MySqlConnector-like configuration");
        final MySqlConnectorConfig connectorConfig = new MySqlConnectorConfig(this.configuration);

        LOG.trace("Setting up database connection");
        final MySqlConnectionConfiguration connectionConfig = new MySqlConnectionConfiguration(configuration);
        final BinlogFieldReader fieldReader = MySqlFieldReaderResolver.resolve(connectorConfig);
        connection = new MySqlConnection(connectionConfig, fieldReader);

        LOG.trace("Setting up value converters");
        valueConverter = new MySqlValueConverters(
                connectorConfig.getDecimalMode(),
                connectorConfig.getTemporalPrecisionMode(),
                connectorConfig.getBigIntUnsignedHandlingMode().asBigIntUnsignedMode(),
                connectorConfig.binaryHandlingMode(),
                connectorConfig.isTimeAdjustedEnabled() ? MySqlValueConverters::adjustTemporal : x -> x,
                connectorConfig.getEventConvertingFailureHandlingMode(),
                connectorConfig.getServiceRegistry()
        );
    }

    @Override
    public List<TableSchema> fetch(final String database,
                                   @Nullable final Set<String> includedSchemas,
                                   @Nullable final Set<String> includedTables,
                                   @Nullable final Set<String> excludedColumns) throws Exception {
        final List<TableSchema> result = new ArrayList<>();

        LOG.trace("Fetching Databases (Schemas)");
        final List<String> availableDbs = connection.availableDatabases();
        final Set<String> selectedDatabases = availableDbs.stream().filter((db) -> {
            // Always exclude built-in databases
            if (mysqlBuiltInDatabases.contains(db)) {
                return false;
            }

            if (includedSchemas != null && !includedSchemas.isEmpty()) {
                return includedSchemas.contains(db);
            }

            // If no includedSchemas provided, we only look at the main 'database' (if provided)
            return database == null || database.isEmpty() || db.equalsIgnoreCase(database);
        }).collect(Collectors.toSet());
        LOG.debug("Selected {} Databases (out of {}): ", selectedDatabases.size(), availableDbs.size());
        selectedDatabases.forEach(db -> LOG.trace("  {}", db));

        LOG.trace("Fetching Tables");
        final List<TableId> allSelectedTables = new ArrayList<>();
        final Tables tables = new Tables();

        for (final String selectedDb : selectedDatabases) {
            LOG.trace("Fetching Tables from Database: {}", selectedDb);

            final Tables databaseTables = new Tables();
            final AtomicInteger totalTablesForDb = new AtomicInteger(0);
            connection.readSchema(
                    databaseTables,
                    selectedDb,
                    null, // schema is null for MySQL
                    Tables.TableFilter.fromPredicate((t) -> {
                        totalTablesForDb.getAndIncrement();
                        if (includedTables != null && !includedTables.isEmpty()) {
                            return includedTables.contains(t.table()) ||
                                    includedTables.contains("%s.%s".formatted(t.catalog(), t.table()));
                        }
                        return true;
                    }),
                    null, // No Column filtering during this step
                    true
            );
            LOG.debug("Selected {} Tables in Database {} (out of {})", databaseTables.size(), selectedDb, totalTablesForDb.get());
            databaseTables.tableIds().forEach(t -> LOG.trace("  {}", t.identifier()));
            allSelectedTables.addAll(databaseTables.tableIds());
            tables.refresh(databaseTables);
        }
        LOG.debug("Selected {} Tables in total: ", allSelectedTables.size());

        // Filter-out Columns, if requested
        final MySqlConnectorConfig connectorConfig = excludedColumns != null
                ? new MySqlConnectorConfig(configuration.edit().with(COLUMN_EXCLUDE_LIST, String.join(",", excludedColumns)).build())
                : new MySqlConnectorConfig(configuration);

        try (final MySqlDatabaseSchema mysqlSchema = new MySqlDatabaseSchema(
                connectorConfig,
                valueConverter,
                CatalogSchemaAndTableTopicNamingStrategy.create(connectorConfig),
                SchemaNameAdjuster.AVRO,
                false)) {

            // Populate the schema with the tables we found
            for (final TableId tId : allSelectedTables) {
                mysqlSchema.refresh(tables.forTable(tId));
                result.add(mysqlSchema.schemaFor(tId));
            }
        } catch (final Exception e) {
            throw new Exception("Failed to load MySQL Schema", e);
        }

        return result;
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (final Exception e) {
            LOG.error("Error closing connection to database", e);
        }
    }
}
