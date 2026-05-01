package io.snyk.skemium.db.mysql;

import io.debezium.config.Configuration;
import io.debezium.relational.TableSchema;
import io.snyk.skemium.WithMySqlContainer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class MySqlTableSchemaFetcherTest extends WithMySqlContainer {

    @Test
    void shouldFetchAllTableSchemas() throws Exception {
        final Configuration config = createMySqlContainerConfiguration(MYSQL_CONTAINER);

        try (final MySqlTableSchemaFetcher fetcher = new MySqlTableSchemaFetcher(config)) {
            final List<TableSchema> tableSchemas = fetcher.fetch(DB_NAME, null, null, null);

            List<String> expectedTableSchemaIds = List.of(
                    "chinook.album",
                    "chinook.artist",
                    "chinook.customer",
                    "chinook.employee",
                    "chinook.genre",
                    "chinook.invoice",
                    "chinook.invoice_line",
                    "chinook.media_type",
                    "chinook.playlist",
                    "chinook.playlist_track",
                    "chinook.track"
            );

            assertEquals(expectedTableSchemaIds.size(), tableSchemas.size());
            final Map<String, TableSchema> tableSchemasMap = tableSchemas.stream()
                    .collect(Collectors.toMap(t -> t.id().toString(), Function.identity()));
            assertTrue(tableSchemasMap.keySet().containsAll(expectedTableSchemaIds));
        }
    }

    @Test
    void shouldFetchSomeTableSchemas() throws Exception {
        final Configuration config = createMySqlContainerConfiguration(MYSQL_CONTAINER);

        try (final MySqlTableSchemaFetcher fetcher = new MySqlTableSchemaFetcher(config)) {
            // MySQL uses catalog, schema is usually null or same as catalog
            final List<TableSchema> tableSchemas = fetcher.fetch(DB_NAME, null, Set.of("customer", "invoice"), null);

            List<String> expectedTableSchemaIds = List.of(
                    "chinook.customer",
                    "chinook.invoice");

            assertEquals(expectedTableSchemaIds.size(), tableSchemas.size());
            final Map<String, TableSchema> tableSchemasMap = tableSchemas.stream()
                    .collect(Collectors.toMap(t -> t.id().toString(), Function.identity()));
            assertTrue(tableSchemasMap.keySet().containsAll(expectedTableSchemaIds));
        }
    }

    @Test
    void shouldExcludeSomeColumns() throws Exception {
        final Configuration config = createMySqlContainerConfiguration(MYSQL_CONTAINER);

        try (final MySqlTableSchemaFetcher fetcher = new MySqlTableSchemaFetcher(config)) {
            final List<TableSchema> tableSchemas = fetcher.fetch(DB_NAME, null,
                    Set.of("customer", "album", "artist"),
                    Set.of(
                            "chinook.customer.FirstName", "chinook.customer.LastName",
                            "chinook.album.ArtistId"
                    ));

            assertEquals(3, tableSchemas.size());
            final Map<String, TableSchema> tableSchemasMap = tableSchemas.stream()
                    .collect(Collectors.toMap(t -> t.id().toString(), Function.identity()));

            final TableSchema customerTableSchema = tableSchemasMap.get("chinook.customer");
            final TableSchema albumTableSchema = tableSchemasMap.get("chinook.album");
            final TableSchema artistTableSchema = tableSchemasMap.get("chinook.artist");

            // Note: In MySQL Chinook, column names are CamelCase (FirstName, LastName, ArtistId)
            assertNull(customerTableSchema.valueSchema().field("FirstName"));
            assertNull(customerTableSchema.valueSchema().field("LastName"));
            assertNull(albumTableSchema.valueSchema().field("ArtistId"));
            assertNotNull(artistTableSchema.valueSchema().field("ArtistId"));
        }
    }
}
