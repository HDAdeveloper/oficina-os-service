package br.com.oficina.os.infrastructure.seguranca;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuração de segurança (prefixo {@code oficina.seguranca}). */
@ConfigurationProperties(prefix = "oficina.seguranca")
public record SegurancaProperties(String jwtSecret, String adminUsuario, String adminSenha, long expiracaoMinutos) {
}
