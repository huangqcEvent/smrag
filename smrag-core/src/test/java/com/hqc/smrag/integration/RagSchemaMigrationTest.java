package com.hqc.smrag.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hqc.smrag.mapper.AuthorizedVectorMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RagSchemaMigrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
        .parse("pgvector/pgvector:pg16")
        .asCompatibleSubstituteFor("postgres");

    @Test
    void createsEmptyManagedMultiKnowledgeBaseSchema() throws Exception {
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
                .target("9")
                .load()
                .migrate();
            long[] legacyAclIds = seedLegacyGlobalAdminAcl(postgres);

            Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();

            assertColumnType(postgres, "knowledge_base", "id", Types.BIGINT);
            assertColumnType(
                postgres,
                "knowledge_document",
                "knowledge_base_id",
                Types.BIGINT
            );
            assertVarcharCapacity(
                postgres,
                "spring_ai_chat_memory",
                "conversation_id",
                160
            );
            assertTableExists(postgres, "knowledge_base_role");
            assertGeneratedColumnExists(postgres, "rag_vector_store", "knowledge_base_id");
            assertGeneratedColumnExists(postgres, "rag_vector_store", "document_id");
            assertGeneratedColumnExists(postgres, "rag_vector_store", "document_acl_version");
            assertGeneratedColumnExists(postgres, "rag_vector_store", "document_version");
            assertIndexExists(postgres, "idx_knowledge_base_role_role_base");
            assertIndexExists(postgres, "idx_rag_vector_store_acl");
            assertForeignKeyExists(postgres, "knowledge_document", "fk_knowledge_document_base");
            assertLegacyGlobalAdminAclRemoved(postgres, legacyAclIds);
            assertSeededAdministratorIsLocked(postgres);
            deleteLegacyAclFixture(postgres, legacyAclIds);
            assertThat(countRows(postgres, "knowledge_base")).isZero();
            assertAuthorizedRetrievalUsesCurrentRelationalAcl(postgres);
        }
    }

    private void assertSeededAdministratorIsLocked(
        PostgreSQLContainer<?> postgres
    ) {
        assertThat(jdbc(postgres).queryForObject(
            "SELECT password_hash FROM sys_user WHERE username = 'admin'",
            String.class
        )).isEqualTo("!BOOTSTRAP_ADMIN_PASSWORD_REQUIRED!");
    }

    private long[] seedLegacyGlobalAdminAcl(PostgreSQLContainer<?> postgres) {
        JdbcTemplate jdbc = jdbc(postgres);
        Long knowledgeBaseId = jdbc.queryForObject("""
            INSERT INTO knowledge_base(code, name, status, created_by)
            VALUES ('legacy-admin-acl', 'Legacy ACL', 'ACTIVE', 1)
            RETURNING id
            """, Long.class);
        jdbc.update("""
            INSERT INTO knowledge_base_role(knowledge_base_id, role_id, created_by)
            VALUES (?, 1, 1), (?, 2, 1)
            """, knowledgeBaseId, knowledgeBaseId);
        Long documentId = jdbc.queryForObject("""
            INSERT INTO knowledge_document(
                name,
                file_type,
                status,
                acl_version,
                knowledge_base_id
            )
            VALUES ('legacy.pdf', 'PDF', 'COMPLETED', 4, ?)
            RETURNING id
            """, Long.class, knowledgeBaseId);
        jdbc.update("""
            INSERT INTO doc_permission(document_id, role_id, can_read)
            VALUES (?, 1, true), (?, 2, true)
            """, documentId, documentId);
        float[] embedding = new float[1536];
        embedding[0] = 1.0F;
        jdbc.update("""
            INSERT INTO rag_vector_store(content, metadata, embedding)
            VALUES (
                'legacy evidence',
                CAST(? AS json),
                CAST(? AS vector)
            )
            """, """
            {"knowledgeBaseId":%d,"documentId":%d,"documentAclVersion":4,"documentVersion":1}
            """.formatted(knowledgeBaseId, documentId), vectorLiteral(embedding));
        Long adminOnlyBaseId = jdbc.queryForObject("""
            INSERT INTO knowledge_base(code, name, status, created_by)
            VALUES ('admin-only-acl', 'Admin only ACL', 'ACTIVE', 1)
            RETURNING id
            """, Long.class);
        jdbc.update("""
            INSERT INTO knowledge_base_role(knowledge_base_id, role_id, created_by)
            VALUES (?, 1, 1)
            """, adminOnlyBaseId);
        return new long[] { knowledgeBaseId, documentId, adminOnlyBaseId };
    }

    private void assertLegacyGlobalAdminAclRemoved(
        PostgreSQLContainer<?> postgres,
        long[] ids
    ) {
        JdbcTemplate jdbc = jdbc(postgres);
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM knowledge_base_role kbr
            INNER JOIN sys_role r ON r.id = kbr.role_id
            WHERE kbr.knowledge_base_id = ? AND r.role_code = 'ADMIN'
            """, Long.class, ids[0])).isZero();
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM doc_permission dp
            INNER JOIN sys_role r ON r.id = dp.role_id
            WHERE dp.document_id = ? AND r.role_code = 'ADMIN'
            """, Long.class, ids[1])).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT acl_version FROM knowledge_document WHERE id = ?",
            Integer.class,
            ids[1]
        )).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
            SELECT (metadata->>'documentAclVersion')::INTEGER
            FROM rag_vector_store
            WHERE document_id = ?
            """, Integer.class, ids[1])).isEqualTo(5);
        assertThat(jdbc.queryForObject(
            "SELECT status FROM knowledge_base WHERE id = ?",
            String.class,
            ids[2]
        )).isEqualTo("DISABLED");
    }

    private void deleteLegacyAclFixture(
        PostgreSQLContainer<?> postgres,
        long[] ids
    ) {
        JdbcTemplate jdbc = jdbc(postgres);
        jdbc.update("DELETE FROM rag_vector_store WHERE document_id = ?", ids[1]);
        jdbc.update("DELETE FROM knowledge_document WHERE id = ?", ids[1]);
        jdbc.update(
            "DELETE FROM knowledge_base WHERE id IN (?, ?)",
            ids[0],
            ids[2]
        );
    }

    private void assertAuthorizedRetrievalUsesCurrentRelationalAcl(
        PostgreSQLContainer<?> postgres
    ) {
        DriverManagerDataSource dataSource = dataSource(postgres);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long userId = jdbc.queryForObject("""
            INSERT INTO sys_user(username, password_hash, nickname, status)
            VALUES ('reader', 'not-used', 'Reader', 'ACTIVE')
            RETURNING id
            """, Long.class);
        Long knowledgeBaseId = jdbc.queryForObject("""
            INSERT INTO knowledge_base(code, name, status, created_by)
            VALUES ('research', 'Research', 'ACTIVE', 1)
            RETURNING id
            """, Long.class);
        jdbc.update("""
            INSERT INTO knowledge_base_role(knowledge_base_id, role_id, created_by)
            VALUES (?, 2, 1)
            """, knowledgeBaseId);
        jdbc.update(
            "INSERT INTO sys_user_role(user_id, role_id) VALUES (?, 2)",
            userId
        );
        Long documentId = jdbc.queryForObject("""
            INSERT INTO knowledge_document(
                name,
                file_type,
                status,
                acl_version,
                knowledge_base_id
            )
            VALUES ('policy.pdf', 'PDF', 'COMPLETED', 1, ?)
            RETURNING id
            """, Long.class, knowledgeBaseId);
        jdbc.update("""
            INSERT INTO doc_permission(document_id, role_id, can_read)
            VALUES (?, 2, true)
            """, documentId);

        float[] embedding = new float[1536];
        embedding[0] = 1.0F;
        String vector = vectorLiteral(embedding);
        String metadata = """
            {"knowledgeBaseId":%d,"documentId":%d,"documentAclVersion":1,"documentVersion":1}
            """.formatted(knowledgeBaseId, documentId);
        jdbc.update("""
            INSERT INTO rag_vector_store(content, metadata, embedding)
            VALUES ('authorized evidence', CAST(? AS json), CAST(? AS vector))
            """, metadata, vector);

        AuthorizedVectorMapper mapper =
            new AuthorizedVectorMapper(jdbc, new ObjectMapper());
        TransactionTemplate transaction = new TransactionTemplate(
            new DataSourceTransactionManager(dataSource)
        );
        List<com.hqc.smrag.mapper.projection.AuthorizedVectorRow> authorized =
            transaction.execute(ignored -> mapper.search(
                knowledgeBaseId,
                userId,
                false,
                null,
                embedding,
                0.1,
                5
            ));
        assertThat(authorized).hasSize(1);

        jdbc.update(
            "DELETE FROM sys_user_role WHERE user_id = ? AND role_id = 2",
            userId
        );
        assertThat(mapper.search(
            knowledgeBaseId,
            userId,
            false,
            null,
            embedding,
            0.1,
            5
        )).isEmpty();
        assertThat(mapper.search(
            knowledgeBaseId,
            1L,
            true,
            null,
            embedding,
            0.1,
            5
        )).hasSize(1);

        jdbc.update(
            "DELETE FROM doc_permission WHERE document_id = ?",
            documentId
        );
        assertThat(mapper.search(
            knowledgeBaseId,
            1L,
            true,
            null,
            embedding,
            0.1,
            5
        )).isEmpty();
    }

    private JdbcTemplate jdbc(PostgreSQLContainer<?> postgres) {
        return new JdbcTemplate(dataSource(postgres));
    }

    private DriverManagerDataSource dataSource(
        PostgreSQLContainer<?> postgres
    ) {
        return new DriverManagerDataSource(
            postgres.getJdbcUrl(),
            postgres.getUsername(),
            postgres.getPassword()
        );
    }

    private String vectorLiteral(float[] embedding) {
        StringBuilder literal = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) literal.append(',');
            literal.append(embedding[index]);
        }
        return literal.append(']').toString();
    }

    private void assertColumnType(
        PostgreSQLContainer<?> postgres,
        String table,
        String column,
        int expectedType
    ) throws Exception {
        try (Connection connection = postgres.createConnection("");
             ResultSet columns = connection.getMetaData()
                 .getColumns(null, "public", table, column)) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getInt("DATA_TYPE")).isEqualTo(expectedType);
        }
    }

    private void assertTableExists(PostgreSQLContainer<?> postgres, String table) throws Exception {
        try (Connection connection = postgres.createConnection("");
             ResultSet tables = connection.getMetaData()
                 .getTables(null, "public", table, new String[] {"TABLE"})) {
            assertThat(tables.next()).isTrue();
        }
    }

    private void assertVarcharCapacity(
        PostgreSQLContainer<?> postgres,
        String table,
        String column,
        int expectedCapacity
    ) throws Exception {
        try (Connection connection = postgres.createConnection("");
             ResultSet columns = connection.getMetaData()
                 .getColumns(null, "public", table, column)) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getInt("COLUMN_SIZE"))
                .isGreaterThanOrEqualTo(expectedCapacity);
        }
    }

    private void assertGeneratedColumnExists(
        PostgreSQLContainer<?> postgres,
        String table,
        String column
    ) throws Exception {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                 SELECT 1
                 FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name = '%s'
                   AND column_name = '%s'
                   AND is_generated = 'ALWAYS'
                 """.formatted(table, column))) {
            assertThat(result.next()).isTrue();
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

    private void assertForeignKeyExists(
        PostgreSQLContainer<?> postgres,
        String table,
        String constraint
    ) throws Exception {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet constraints = statement.executeQuery("""
                 SELECT 1
                 FROM pg_constraint
                 WHERE conname = '%s'
                   AND conrelid = 'public.%s'::regclass
                   AND contype = 'f'
                 """.formatted(constraint, table))) {
            assertThat(constraints.next()).isTrue();
        }
    }

    private long countRows(PostgreSQLContainer<?> postgres, String table) throws Exception {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertThat(rows.next()).isTrue();
            return rows.getLong(1);
        }
    }
}
