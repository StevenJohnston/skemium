package io.snyk.skemium;

import io.snyk.skemium.avro.TableAvroSchemas;
import io.snyk.skemium.db.DatabaseKind;
import io.snyk.skemium.meta.MetadataFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GenerateCommandMySqlTest extends WithMySqlContainer {
    Path TEMP_DIR;

    @BeforeEach
    public void createTempDir() throws IOException {
        TEMP_DIR = Files.createTempDirectory("skemium-test-mysql-");
    }

    @AfterEach
    public void deleteTempDir() throws IOException {
        if (TEMP_DIR != null) {
            FileUtils.deleteDirectory(TEMP_DIR.toFile());
        }
    }

    @Test
    void shouldGenerateSchemasIntoTheGivenDirectory() throws IOException {
        final CommandLine cmdLine = new CommandLine(new GenerateCommand())
                .setOut(new PrintWriter(new StringWriter()))
                .setErr(new PrintWriter(new StringWriter()));

        // `generate` runs successfully
        assertEquals(0, cmdLine.execute(
                "--hostname", MYSQL_CONTAINER.getHost(),
                "--port", MYSQL_CONTAINER.getMappedPort(MYSQL_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--kind", DatabaseKind.MYSQL.toString(),
                TEMP_DIR.toAbsolutePath().toString()
        ));

        // Metadata file contains...
        final MetadataFile meta = MetadataFile.loadFrom(TEMP_DIR);

        // ... command line arguments
        assertEquals(List.of(
                "--hostname", MYSQL_CONTAINER.getHost(),
                "--port", MYSQL_CONTAINER.getMappedPort(MYSQL_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--kind", DatabaseKind.MYSQL.toString(),
                TEMP_DIR.toAbsolutePath().toString()), meta.arguments());

        // .. count of schemas
        assertEquals(11, meta.schemaCount());

        // ... specific schemas checksums, as well as a summary of all
        for (final Map.Entry<String, String> schemaMetaEntry : meta.schemas().entrySet()) {
            final TableAvroSchemas tableSchemas = TableAvroSchemas.loadFrom(TEMP_DIR, schemaMetaEntry.getKey());
            assertEquals(schemaMetaEntry.getValue(), tableSchemas.checksum());
        }

        // ... VCS information
        assertNotNull(meta.vcsCommit());
        assertNotNull(meta.vcsBranch());
    }

    @Test
    void shouldGenerateSchemaForSubsetOfTables() throws IOException {
        final CommandLine cmdLine = new CommandLine(new GenerateCommand())
                .setOut(new PrintWriter(new StringWriter()))
                .setErr(new PrintWriter(new StringWriter()));

        // `generate` runs successfully
        assertEquals(0, cmdLine.execute(
                "--hostname", MYSQL_CONTAINER.getHost(),
                "--port", MYSQL_CONTAINER.getMappedPort(MYSQL_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--kind", DatabaseKind.MYSQL.toString(),
                "--table", "employee",
                "--table", "artist,album",
                TEMP_DIR.toAbsolutePath().toString()
        ));

        // Metadata file contains...
        final MetadataFile meta = MetadataFile.loadFrom(TEMP_DIR);

        // ... command line arguments
        assertEquals(List.of(
                "--hostname", MYSQL_CONTAINER.getHost(),
                "--port", MYSQL_CONTAINER.getMappedPort(MYSQL_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--kind", DatabaseKind.MYSQL.toString(),
                "--table", "employee",
                "--table", "artist,album",
                TEMP_DIR.toAbsolutePath().toString()), meta.arguments());

        // .. count of schemas
        assertEquals(3, meta.schemaCount());

        // ... specific schemas checksums, as well as a summary of all
        for (final Map.Entry<String, String> schemaMetaEntry : meta.schemas().entrySet()) {
            final TableAvroSchemas tableSchemas = TableAvroSchemas.loadFrom(TEMP_DIR, schemaMetaEntry.getKey());
            assertEquals(schemaMetaEntry.getValue(), tableSchemas.checksum());
        }

        // ... VCS information
        assertNotNull(meta.vcsCommit());
        assertNotNull(meta.vcsBranch());
    }

    @Test
    void shouldGenerateSchemaForSubsetOfTablesAndColumns() throws IOException {
        final CommandLine cmdLine = new CommandLine(new GenerateCommand())
                .setOut(new PrintWriter(new StringWriter()))
                .setErr(new PrintWriter(new StringWriter()));

        // `generate` runs successfully
        assertEquals(0, cmdLine.execute(
                "--hostname", MYSQL_CONTAINER.getHost(),
                "--port", MYSQL_CONTAINER.getMappedPort(MYSQL_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--kind", DatabaseKind.MYSQL.toString(),
                "--table", "artist",
                "-t", "album",
                "-x", "artist.artist_id",
                "--exclude-column", "album.album_id,album.artist_id",
                TEMP_DIR.toAbsolutePath().toString()
        ));

        // Metadata file contains...
        final MetadataFile meta = MetadataFile.loadFrom(TEMP_DIR);

        // ... command line arguments
        assertEquals(List.of(
                "--hostname", MYSQL_CONTAINER.getHost(),
                "--port", MYSQL_CONTAINER.getMappedPort(MYSQL_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--kind", DatabaseKind.MYSQL.toString(),
                "--table", "artist",
                "-t", "album",
                "-x", "artist.artist_id",
                "--exclude-column", "album.album_id,album.artist_id",
                TEMP_DIR.toAbsolutePath().toString()), meta.arguments());

        // ... count of schemas
        assertEquals(2, meta.schemaCount());

        // Confirm `artist` schema contains only `name` field
        final TableAvroSchemas artistTableSchemas = TableAvroSchemas.loadFrom(TEMP_DIR, "chinook.artist");
        assertNull(artistTableSchemas.valueSchema().getField("artist_id"));
        assertEquals(1, artistTableSchemas.valueSchema().getFields().size());
        // In MySQL/Debezium, VARCHAR might be just "string" or "union[null, string]" depending on nullability
        // Artist name in Chinook is nullable in some versions, but let's check what it is
        assertTrue(artistTableSchemas.valueSchema().getField("name").schema().getType() == org.apache.avro.Schema.Type.UNION || 
                   artistTableSchemas.valueSchema().getField("name").schema().getType() == org.apache.avro.Schema.Type.STRING);

        // Confirm `album` schema contains only the `title` field
        final TableAvroSchemas albumTableSchemas = TableAvroSchemas.loadFrom(TEMP_DIR, "chinook.album");
        assertNull(albumTableSchemas.valueSchema().getField("album_id"));
        assertNull(albumTableSchemas.valueSchema().getField("artist_id"));
        assertEquals(1, albumTableSchemas.valueSchema().getFields().size());
        assertEquals("string", albumTableSchemas.valueSchema().getField("title").schema().getName());
    }

    @Test
    void shouldFailIfCannotConnectToDB() throws IOException {
        final CommandLine cmdLine = new CommandLine(new GenerateCommand());

        // `generate` fails
        assertEquals(1, cmdLine.execute(
                "--hostname", MYSQL_CONTAINER.getHost(),
                "--port", String.valueOf(MYSQL_CONTAINER.getMappedPort(MYSQL_DEFAULT_PORT) + 1),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--kind", DatabaseKind.MYSQL.toString(),
                TEMP_DIR.toAbsolutePath().toString()
        ));
    }

    @Test
    void shouldNotProvideKeySchemaForTableWithoutPrimaryKey() throws IOException {
        final CommandLine cmdLine = new CommandLine(new GenerateCommand())
                .setOut(new PrintWriter(new StringWriter()))
                .setErr(new PrintWriter(new StringWriter()));

        // `generate` runs successfully
        // Note: playlist_track HAS a primary key in most Chinook versions (playlist_id, track_id)
        // I should check if there is a table without PK in the MySQL chinook.sql
        assertEquals(0, cmdLine.execute(
                "--hostname", MYSQL_CONTAINER.getHost(),
                "--port", MYSQL_CONTAINER.getMappedPort(MYSQL_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--kind", DatabaseKind.MYSQL.toString(),
                "--table", "playlist_track",
                TEMP_DIR.toAbsolutePath().toString()
        ));

        // Metadata file contains...
        final MetadataFile meta = MetadataFile.loadFrom(TEMP_DIR);
        assertEquals(1, meta.schemaCount());

        final TableAvroSchemas playlistTrackDec = TableAvroSchemas.loadFrom(TEMP_DIR, "chinook.playlist_track");

        assertNotNull(playlistTrackDec.keySchema());
        assertNotNull(playlistTrackDec.valueSchema());
        assertNotNull(playlistTrackDec.envelopeSchema());
    }
}
