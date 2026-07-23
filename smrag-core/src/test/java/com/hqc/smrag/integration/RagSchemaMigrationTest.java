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
                .target("7")
                .load()
                .migrate();
            insertAclBackfillFixtures(postgres);
            Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();

            assertColumnType(postgres, "knowledge_chunk", "vector_id", "uuid");
            assertColumnType(postgres, "knowledge_document", "checksum", "varchar");
            assertColumnType(postgres, "knowledge_document", "storage_type", "varchar");
            assertColumnType(postgres, "knowledge_document", "object_key", "varchar");
            assertColumnType(postgres, "knowledge_document", "parsed_text_object_key", "varchar");
            assertColumnType(postgres, "knowledge_document", "acl_version", "int4");
            assertTableExists(postgres, "rag_vector_store");
            assertTableExists(postgres, "spring_ai_chat_memory");
            assertIndexExists(postgres, "rag_vector_store_embedding_hnsw_idx");
            assertIndexExists(postgres, "idx_doc_permission_role_document");
            assertAclBackfill(postgres);
        }
    }

    private void insertAclBackfillFixtures(PostgreSQLContainer<?> postgres) throws Exception {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO knowledge_document (id, name, acl_version)
                VALUES (9001, 'granted.txt', 2), (9002, 'denied.txt', 5)
                """);
            statement.executeUpdate("""
                INSERT INTO doc_permission (document_id, role_id, can_read)
                SELECT 9001, id, true FROM sys_role WHERE role_code = 'ADMIN'
                """);
            statement.executeUpdate("""
                INSERT INTO rag_vector_store (content, metadata)
                VALUES
                    (
                        'granted',
                        '{"documentId":9001,"aclVersion":2,"aclDenied":false,'
                        '"aclRole_ADMIN":true,"aclRoleId_999":true}'
                    ),
                    (
                        'denied',
                        '{"documentId":9002,"aclVersion":5,"aclDenied":false,'
                        '"aclRole_STALE":true,"aclRoleId_999":true}'
                    )
                """);
        }
    }

    private void assertAclBackfill(PostgreSQLContainer<?> postgres) throws Exception {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                 SELECT
                     metadata->>'documentId' AS document_id,
                     metadata->>'aclVersion' AS acl_version,
                     metadata->>'aclDenied' AS acl_denied,
                     metadata->>(
                         'aclRoleId_' || (
                             SELECT id::text FROM sys_role WHERE role_code = 'ADMIN'
                         )
                     ) AS admin_role,
                     metadata->>'aclRole_ADMIN' AS legacy_admin_role,
                     metadata->>'aclRole_STALE' AS legacy_stale_role,
                     metadata->>'aclRoleId_999' AS stale_role_id
                 FROM rag_vector_store
                 WHERE metadata->>'documentId' IN ('9001', '9002')
                 ORDER BY metadata->>'documentId'
                 """)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("document_id")).isEqualTo("9001");
            assertThat(rows.getString("acl_version")).isEqualTo("2");
            assertThat(rows.getString("acl_denied")).isEqualTo("false");
            assertThat(rows.getString("admin_role")).isEqualTo("true");
            assertThat(rows.getString("legacy_admin_role")).isNull();
            assertThat(rows.getString("stale_role_id")).isNull();
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("document_id")).isEqualTo("9002");
            assertThat(rows.getString("acl_version")).isEqualTo("5");
            assertThat(rows.getString("acl_denied")).isEqualTo("true");
            assertThat(rows.getString("admin_role")).isNull();
            assertThat(rows.getString("legacy_stale_role")).isNull();
            assertThat(rows.getString("stale_role_id")).isNull();
            assertThat(rows.next()).isFalse();
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
