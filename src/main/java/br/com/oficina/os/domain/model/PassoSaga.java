package br.com.oficina.os.domain.model;

import java.time.Instant;

/** Linha do diário da Saga: o que aconteceu (comando enviado, evento recebido, mudança de estado) e quando. */
public record PassoSaga(Instant em, String tipo, String detalhe) {
}
