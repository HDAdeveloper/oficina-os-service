package br.com.oficina.os.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Instant;

@Embeddable
class PassoEmb {

    @Column(name = "em", nullable = false)
    Instant em;

    @Column(nullable = false, length = 40)
    String tipo;

    @Column(length = 1000)
    String detalhe;

    protected PassoEmb() {
    }

    PassoEmb(Instant em, String tipo, String detalhe) {
        this.em = em;
        this.tipo = tipo;
        this.detalhe = detalhe == null ? null : detalhe.substring(0, Math.min(detalhe.length(), 1000));
    }
}
