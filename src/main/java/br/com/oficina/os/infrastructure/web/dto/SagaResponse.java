package br.com.oficina.os.infrastructure.web.dto;

import br.com.oficina.os.domain.model.Saga;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Estado e diário da Saga de uma OS: quem respondeu o quê, em que ordem, e onde ela está agora. */
public record SagaResponse(String id, Long osId, String correlationId, String estado, String orcamentoId,
                           BigDecimal valorTotal, String pagamentoId, String reservaId, String motivo,
                           int tentativas, Instant prazo, Instant criadaEm, Instant atualizadaEm,
                           List<Passo> passos) {

    public record Passo(Instant em, String tipo, String detalhe) {
    }

    public static SagaResponse de(Saga s) {
        return new SagaResponse(s.getId(), s.getOsId(), s.getCorrelationId(), s.getEstado().name(),
                s.getOrcamentoId(), s.getValorTotal(), s.getPagamentoId(), s.getReservaId(), s.getMotivo(),
                s.getTentativas(), s.getPrazo(), s.getCriadaEm(), s.getAtualizadaEm(),
                s.getPassos().stream().map(p -> new Passo(p.em(), p.tipo(), p.detalhe())).toList());
    }
}
