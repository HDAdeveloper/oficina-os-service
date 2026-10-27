package br.com.oficina.os.infrastructure.mensageria;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Prefixo {@code oficina.mensageria}. {@code endpoint} só é preenchido contra o LocalStack. */
@ConfigurationProperties(prefix = "oficina.mensageria")
public record MensageriaProperties(boolean habilitada, String regiao, String endpoint, String filaEventos,
                                   String topicoComandosArn, long intervaloRelayMs, int loteRelay,
                                   long intervaloPrazosMs) {
}
