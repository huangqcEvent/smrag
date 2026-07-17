package com.hqc.smrag.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RagSchemaMigrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
        .parse("pgvector/pgvector:pg16")
        .asCompatibleSubstituteFor("postgres");

    @Test
    void createsManagedRagSchema() throws Exception {
        assumeTrue(
            DockerClientFactory.instance().isDockerAvailable(),
            "Docker is required for the PGVector migration integration test"
        );

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PGVECTOR_IMAGE)) {
            postgres.start();

            Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();

            assertColumnType(postgres, "knowledge_chunk", "vector_id", "uuid");
            assertColumnType(postgres, "knowledge_document", "checksum", "varchar");
            assertTableExists(postgres, "rag_vector_store");
            assertTableExists(postgres, "spring_ai_chat_memory");
            assertIndexExists(postgres, "rag_vector_store_embedding_hnsw_idx");
        }
    }

    private void assertColumnType(
        PostgreSQLContainer<?> postgres,
        String table,
        String column,
        String expectedType
    ) throws Exception {
        try (Connection connection = postgres.createConnection("");
             ResultSet columns = connection.getMetaData()
                 .getColumns(null, "public", table, column)) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getString("TYPE_NAME")).isEqualTo(expectedType);
        }
    }

    private void assertTableExists(PostgreSQLContainer<?> postgres, String table) throws Exception {
        try (Connection connection = postgres.createConnection("");
             ResultSet tables = connection.getMetaData()
                 .getTables(null, "public", table, new String[] {"TABLE"})) {
            assertThat(tables.next()).isTrue();
        }
    }

    private void assertIndexExists(PostgreSQLContainer<?> postgres, String index) throws Exception {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet indexes = statement.executeQuery("""
                 SELECT 1
                 FROM pg_indexes
                 WHERE schemaname = 'public' AND indexname = '%s'
                 """.formatted(index))) {
            assertThat(indexes.next()).isTrue();
        }
    }
}
