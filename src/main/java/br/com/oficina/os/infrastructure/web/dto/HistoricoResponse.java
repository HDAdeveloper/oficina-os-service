package br.com.oficina.os.infrastructure.web.dto;

import br.com.oficina.os.domain.model.HistoricoStatus;

import java.time.Instant;

public record HistoricoResponse(String de, String para, Instant em, String motivo) {

    public static HistoricoResponse de(HistoricoStatus h) {
        return new HistoricoResponse(h.de() == null ? null : h.de().name(), h.para().name(), h.em(), h.motivo());
    }
}
