package br.com.oficina.os.infrastructure.persistence;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Um único PostgreSQL de verdade (em contêiner, precisa do Docker rodando) compartilhado pelos
 * testes de persistência. O Flyway cria o esquema e o Hibernate o valida (ddl-auto=validate).
 */
public abstract class PostgresTestBase {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    protected static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
        // SQS e SNS só nos testes de mensageria; aqui o foco é o banco
        registro.add("oficina.mensageria.habilitada", () -> "false");
    }
}
