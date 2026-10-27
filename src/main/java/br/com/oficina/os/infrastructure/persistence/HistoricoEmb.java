package br.com.oficina.os.infrastructure.persistence;

import br.com.oficina.os.domain.model.StatusOS;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.Instant;

@Embeddable
class HistoricoEmb {

    @Enumerated(EnumType.STRING)
    @Column(name = "de")
    StatusOS de;

    @Enumerated(EnumType.STRING)
    @Column(name = "para", nullable = false)
    StatusOS para;

    @Column(name = "em", nullable = false)
    Instant em;

    @Column(length = 500)
    String motivo;

    protected HistoricoEmb() {
    }

    HistoricoEmb(StatusOS de, StatusOS para, Instant em, String motivo) {
        this.de = de;
        this.para = para;
        this.em = em;
        this.motivo = motivo;
    }
}
