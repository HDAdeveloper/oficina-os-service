package br.com.oficina.os.application.port.out;

import java.time.Instant;

/** Porta de saída: ids das mensagens já tratadas (idempotência da entrega at-least-once). */
public interface MensagemProcessadaRepository {

    /** Registra a mensagem; devolve {@code false} se ela já tinha sido registrada antes. */
    boolean registrar(String messageId, Instant em);
}
