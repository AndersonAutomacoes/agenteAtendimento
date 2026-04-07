-- Uma vez: remove vector_store criada com vector(>2000) antes de existir spring.ai.vectorstore.pgvector.dimensions.
-- Flyway corre antes do PgVectorStore; a app recria a tabela com dimensão alinhada ao embedding (768).
DROP TABLE IF EXISTS vector_store CASCADE;
