package br.com.oficina.os.bdd;

import br.com.oficina.os.infrastructure.persistence.PostgresTestBase;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/** Sobe a aplicação inteira (Spring Security, JPA, Flyway, orquestrador) sobre um PostgreSQL de verdade. */
@CucumberContextConfiguration
@SpringBootTest
@AutoConfigureMockMvc
public class CucumberSpringConfig extends PostgresTestBase {
}
