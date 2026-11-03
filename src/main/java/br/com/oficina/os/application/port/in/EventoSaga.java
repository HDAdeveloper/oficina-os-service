package br.com.oficina.os.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;

/** Eventos que o Billing e o Execution enviam ao orquestrador (docs/eventos.md). */
public sealed interface EventoSaga {

    record OrcamentoCriado(String orcamentoId, BigDecimal valorTotal) implements EventoSaga {
    }

    record OrcamentoFalhou(String motivo) implements EventoSaga {
    }

    record OrcamentoCancelado() implements EventoSaga {
    }

    /** Pagamento por link: o cliente ainda vai pagar; {@code expiraEm} é até quando o link vale. */
    record LinkPagamentoGerado(String pagamentoId, String link, Instant expiraEm) implements EventoSaga {
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
