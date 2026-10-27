package br.com.oficina.os.domain.model;

import java.time.Instant;

/** Registro de uma mudança de status da OS. {@code de} é nulo na abertura. */
public record HistoricoStatus(StatusOS de, StatusOS para, Instant em, String motivo) {
}
