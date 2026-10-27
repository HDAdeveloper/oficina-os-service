package br.com.oficina.os.application.port.in;

/** Metadados do envelope da mensagem recebida. */
public record ContextoMensagem(String messageId, String sagaId, Long osId, String correlationId) {
}
