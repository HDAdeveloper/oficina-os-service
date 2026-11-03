package br.com.oficina.os.infrastructure.persistence;

import br.com.oficina.os.domain.model.EstadoSaga;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Estado do orquestrador. O {@code versao} protege contra duas réplicas tratando a mesma Saga ao mesmo tempo. */
@Entity
@Table(name = "saga_instance")
public class SagaEntity {

    @Id
    String id;

    @Version
    @Column(name = "versao", nullable = false)
    Long versao;

    @Column(name = "os_id", nullable = false)
    Long osId;

    @Column(name = "correlation_id")
    String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    EstadoSaga estado;

    @Column(name = "orcamento_id")
    String orcamentoId;

    @Column(name = "valor_total")
    BigDecimal valorTotal;

    @Column(name = "metodo_pagamento")
    String metodoPagamento;

    @Column(name = "pagamento_id")
    String pagamentoId;

    @Column(name = "link_pagamento", length = 500)
    String linkPagamento;

    @Column(name = "reserva_id")
    String reservaId;

    @Column(length = 1000)
    String motivo;

    @Column(nullable = false)
    int tentativas;

    @Column
    Instant prazo;

    @Column(name = "criada_em", nullable = false)
    Instant criadaEm;

    @Column(name = "atualizada_em", nullable = false)
    Instant atualizadaEm;

    @ElementCollection
    @CollectionTable(name = "saga_passo", joinColumns = @JoinColumn(name = "saga_id"))
    @OrderColumn(name = "ordem")
    List<PassoEmb> passos = new ArrayList<>();

    protected SagaEntity() {
    }
}
