package io.snyk.skemium;

import io.confluent.kafka.schemaregistry.CompatibilityLevel;
import io.snyk.skemium.db.DatabaseKind;
import io.snyk.skemium.helpers.JSON;
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
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompareCommandMySqlTest extends WithMySqlContainer {
    Path CURR_DIR;
    Path NEXT_DIR;
    Path OUTPUT_FILE;

    @BeforeEach
    public void createTempFiles() throws IOException {
        CURR_DIR = Files.createTempDirectory("skemium-test-mysql-curr-");
        NEXT_DIR = Files.createTempDirectory("skemium-test-mysql-next-");
        OUTPUT_FILE = Files.createTempFile("skemium-test-mysql-compare-result", ".json");
    }

    @AfterEach
    public void deleteTempFiles() throws IOException {
        if (CURR_DIR != null) {
            FileUtils.deleteDirectory(CURR_DIR.toFile());
        }
        if (NEXT_DIR != null) {
            FileUtils.deleteDirectory(NEXT_DIR.toFile());
        }
        if (OUTPUT_FILE != null) {
            Files.deleteIfExists(OUTPUT_FILE);
        }
    }

    @Test
    public void shouldReportNoIncompatibilitiesWhenComparingLikeForLike() throws IOException {
        final CommandLine cmdLine = new CommandLine(new GenerateCommand())
                .setOut(new PrintWriter(new StringWriter()))
                .setErr(new PrintWriter(new StringWriter()));

        assertEquals(0, cmdLine.execute(
                "--hostname", MYSQL_CONTAINER.getHost(),
                "--port", MYSQL_CONTAINER.getMappedPort(MYSQL_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--kind", DatabaseKind.MYSQL.toString(),
                "--table", "customer,genre,track,playlist,employee,album,artist",
                CURR_DIR.toAbsolutePath().toString()
        ));
        FileUtils.copyDirectory(CURR_DIR.toFile(), NEXT_DIR.toFile());

        final CompareResult res = CompareResult.build(CURR_DIR, NEXT_DIR, CompatibilityLevel.BACKWARD);

        assertEquals(CompatibilityLevel.BACKWARD, res.compatibilityLevel());
        // In MySQL, identifiers are typically catalog.table
        final Map<String, List<String>> expected = Map.of(
                "chinook.customer", List.of(),
                "chinook.genre", List.of(),
                "chinook.track", List.of(),
                "chinook.employee", List.of(),
                "chinook.playlist", List.of(),
                "chinook.album", List.of(),
                "chinook.artist", List.of()
        );
        assertEquals(expected, res.keyIncompatibilities());
        assertEquals(expected, res.valueIncompatibilities());
        assertEquals(expected, res.envelopeIncompatibilities());
        assertEquals(Set.of(), res.removedTables());
        assertEquals(Set.of(), res.addedTables());
    }

    @Test
    public void shouldReportIncompatibleSchemaChange_MakingColumnNotNull() throws IOException, SQLException {
        final CommandLine cmdLine = new CommandLine(new GenerateCommand())
                .setOut(new PrintWriter(new StringWriter()))
                .setErr(new PrintWriter(new StringWriter()));

        // First, generate the schema for the `artist` table only
        assertEquals(0, cmdLine.execute(
                "--hostname", MYSQL_CONTAINER.getHost(),
                "--port", MYSQL_CONTAINER.getMappedPort(MYSQL_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--kind", DatabaseKind.MYSQL.toString(),
                "--table", "artist",
                CURR_DIR.toAbsolutePath().toString()
        ));

        // Alter schema of `artist` table: make column `name` not null:
        // this is a NON BACKWARD COMPATIBLE change
        try (final Connection connection = getConnection()) {
            // MySQL syntax for ALTER COLUMN SET NOT NULL is MODIFY
            connection.prepareStatement("ALTER TABLE artist MODIFY name VARCHAR(120) NOT NULL").execute();
        }

        // Then, generate the new schema for the `artist` table only
        assertEquals(0, cmdLine.execute(
                "--hostname", MYSQL_CONTAINER.getHost(),
                "--port", MYSQL_CONTAINER.getMappedPort(MYSQL_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--kind", DatabaseKind.MYSQL.toString(),
                "--table", "artist",
                NEXT_DIR.toAbsolutePath().toString()
        ));

        // Change is not BACKWARD compatible
        CompareResult res = CompareResult.build(CURR_DIR, NEXT_DIR, CompatibilityLevel.BACKWARD);
        assertEquals(0, res.keyIncompatibilitiesTotal());
        assertEquals(2, res.valueIncompatibilitiesTotal());
        assertEquals(3, res.envelopeIncompatibilitiesTotal());
        assertEquals(Set.of(), res.removedTables());
        assertEquals(Set.of(), res.addedTables());

        // Change would be BACKWARD compatible, if it was in reverse (from NEXT to CURR)
        res = CompareResult.build(NEXT_DIR, CURR_DIR, CompatibilityLevel.BACKWARD);
        assertEquals(0, res.keyIncompatibilitiesTotal());
        assertEquals(0, res.valueIncompatibilitiesTotal());
        assertEquals(0, res.envelopeIncompatibilitiesTotal());

        // Change is FORWARD compatible
        res = CompareResult.build(CURR_DIR, NEXT_DIR, CompatibilityLevel.FORWARD);
        assertEquals(0, res.keyIncompatibilitiesTotal());
        assertEquals(0, res.valueIncompatibilitiesTotal());
        assertEquals(0, res.envelopeIncompatibilitiesTotal());
    }

    @Test
    public void shouldReportIncompatibleSchemaChange_AddMandatoryColumnWithoutDefaultValue() throws IOException, SQLException {
        final CommandLine cmdLine = new CommandLine(new GenerateCommand())
                .setOut(new PrintWriter(new StringWriter()))
                .setErr(new PrintWriter(new StringWriter()));

        // First, generate the schema for the `artist` table only
        assertEquals(0, cmdLine.execute(
                "--hostname", MYSQL_CONTAINER.getHost(),
                "--port", MYSQL_CONTAINER.getMappedPort(MYSQL_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--kind", DatabaseKind.MYSQL.toString(),
                "--table", "artist",
                CURR_DIR.toAbsolutePath().toString()
        ));

        // Alter schema of `artist` table: add mandatory column `genre`, without adding a default value:
        // this is a NON BACKWARD COMPATIBLE change
        try (final Connection connection = getConnection()) {
            connection.prepareStatement("ALTER TABLE artist ADD COLUMN genre VARCHAR(50) NOT NULL").execute();
        }

        // Then, generate the new schema for the `artist` table only
        assertEquals(0, cmdLine.execute(
                "--hostname", MYSQL_CONTAINER.getHost(),
                "--port", MYSQL_CONTAINER.getMappedPort(MYSQL_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--kind", DatabaseKind.MYSQL.toString(),
                "--table", "artist",
                NEXT_DIR.toAbsolutePath().toString()
        ));

        // Change is not BACKWARD compatible
        CompareResult res = CompareResult.build(CURR_DIR, NEXT_DIR, CompatibilityLevel.BACKWARD);
        assertEquals(0, res.keyIncompatibilitiesTotal());
        assertEquals(2, res.valueIncompatibilitiesTotal());
        assertEquals(3, res.envelopeIncompatibilitiesTotal());
    }

    @Test
    public void shouldReportIncompatibleSchemaChange_SaveToOutputFile() throws IOException, SQLException {
        final CommandLine generateCLI = new CommandLine(new GenerateCommand())
                .setOut(new PrintWriter(new StringWriter()))
                .setErr(new PrintWriter(new StringWriter()));

        // First, generate the schema for the `employee` table only
        assertEquals(0, generateCLI.execute(
                "--hostname", MYSQL_CONTAINER.getHost(),
                "--port", MYSQL_CONTAINER.getMappedPort(MYSQL_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--kind", DatabaseKind.MYSQL.toString(),
                "--table", "employee",
                CURR_DIR.toAbsolutePath().toString()
        ));

        // Alter schema of `employee` table: make column `title` not null:
        // this is a NON BACKWARD COMPATIBLE change
        try (final Connection connection = getConnection()) {
            connection.prepareStatement("ALTER TABLE employee MODIFY title VARCHAR(30) NOT NULL").execute();
        }

        // Then, generate the new schema for the `employee` table only
        assertEquals(0, generateCLI.execute(
                "--hostname", MYSQL_CONTAINER.getHost(),
                "--port", MYSQL_CONTAINER.getMappedPort(MYSQL_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--kind", DatabaseKind.MYSQL.toString(),
                "--table", "employee",
                NEXT_DIR.toAbsolutePath().toString()
        ));

        final CommandLine compareCLI = new CommandLine(new CompareCommand())
                .setOut(new PrintWriter(new StringWriter()))
                .setErr(new PrintWriter(new StringWriter()));

        // Change is not BACKWARD compatible, so the command is expected to fail (exit == 1).
        // Output saved to file.
        assertEquals(1, compareCLI.execute(
                "--compatibility", CompatibilityLevel.BACKWARD.toString(),
                "--output", OUTPUT_FILE.toAbsolutePath().toString(),
                CURR_DIR.toAbsolutePath().toString(),
                NEXT_DIR.toAbsolutePath().toString()
        ));

        final CompareResult resFromOutputFile = JSON.from(OUTPUT_FILE.toFile(), CompareResult.class);
        assertEquals(0, resFromOutputFile.keyIncompatibilitiesTotal());
        assertEquals(2, resFromOutputFile.valueIncompatibilitiesTotal());
        assertEquals(3, resFromOutputFile.envelopeIncompatibilitiesTotal());
        assertEquals(Set.of(), resFromOutputFile.removedTables());
        assertEquals(Set.of(), resFromOutputFile.addedTables());
    }

    @Test
    public void shouldReportRemovedAndAddedTables() throws IOException {
        final CommandLine cmdLine = new CommandLine(new GenerateCommand())
                .setOut(new PrintWriter(new StringWriter()))
                .setErr(new PrintWriter(new StringWriter()));

        // CURR to have: customer, genre, track
        assertEquals(0, cmdLine.execute(
                "--hostname", MYSQL_CONTAINER.getHost(),
                "--port", MYSQL_CONTAINER.getMappedPort(MYSQL_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--kind", DatabaseKind.MYSQL.toString(),
                "--table", "customer,genre,track",
                CURR_DIR.toAbsolutePath().toString()
        ));
        // NEXT to have: genre, track, playlist
        assertEquals(0, cmdLine.execute(
                "--hostname", MYSQL_CONTAINER.getHost(),
                "--port", MYSQL_CONTAINER.getMappedPort(MYSQL_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--kind", DatabaseKind.MYSQL.toString(),
                "--table", "genre,track,playlist",
                NEXT_DIR.toAbsolutePath().toString()
        ));

        final CompareResult res = CompareResult.build(CURR_DIR, NEXT_DIR, CompatibilityLevel.BACKWARD);

        assertEquals(CompatibilityLevel.BACKWARD, res.compatibilityLevel());
        assertEquals(Set.of("chinook.customer"), res.removedTables());
        assertEquals(Set.of("chinook.playlist"), res.addedTables());
        assertEquals(Map.of(
                "chinook.genre", List.of(),
                "chinook.track", List.of()
        ), res.keyIncompatibilities());
    }
}
