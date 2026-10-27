package br.com.oficina.os.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Prefixo {@code oficina.saga}. {@code prazoRespostaSegundos}: quanto esperar a resposta de um passo antes de
 * reenviar o comando; {@code maxReenvios}: quantos reenvios antes de compensar ou deixar pendente.
 */
@ConfigurationProperties(prefix = "oficina.saga")
public record SagaProperties(long prazoRespostaSegundos, int maxReenvios) {
}
