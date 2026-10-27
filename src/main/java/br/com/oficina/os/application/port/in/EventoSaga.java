package br.com.oficina.os.application.port.in;

import java.math.BigDecimal;

/** Eventos que o Billing e o Execution enviam ao orquestrador (docs/eventos.md). */
public sealed interface EventoSaga {

    record OrcamentoCriado(String orcamentoId, BigDecimal valorTotal) implements EventoSaga {
    }

    record OrcamentoFalhou(String motivo) implements EventoSaga {
    }

    record OrcamentoCancelado() implements EventoSaga {
    }

    record PagamentoAprovado(String pagamentoId, String orcamentoId) implements EventoSaga {
    }

    record PagamentoRecusado(String orcamentoId, String motivo) implements EventoSaga {
    }

    record PagamentoEstornado(String pagamentoId) implements EventoSaga {
    }

    record EstoqueReservado(String reservaId) implements EventoSaga {
    }

    /** {@code descricao} resume as peças em falta (ou o motivo de um pedido inválido). */
    record EstoqueInsuficiente(String descricao) implements EventoSaga {
    }

    record EstoqueLiberado() implements EventoSaga {
    }

    record ExecucaoIniciada() implements EventoSaga {
    }

    record ExecucaoFinalizada() implements EventoSaga {
    }

    record ExecucaoFalhou(String motivo) implements EventoSaga {
    }
}
