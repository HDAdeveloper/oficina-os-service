package br.com.oficina.os.infrastructure.mensageria;

import br.com.oficina.os.application.port.in.EventoSaga;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsumidorEventosTest {

    private static final String CORPO = """
            {"messageId":"m-9","sagaId":"s-9","osId":5,"type":"PAGAMENTO_APROVADO","version":1,
             "occurredAt":"2026-10-23T14:05:00Z","correlationId":"c-9",
             "payload":{"pagamentoId":"p-1","orcamentoId":"o-1"},"campoNovo":"ignorado"}""";

    private final ProcessadorFalso processador = new ProcessadorFalso();
    private final ConsumidorEventos consumidor =
            new ConsumidorEventos(new ObjectMapper(), new EventoMapper(), processador);

    @Test
    void leOEnvelopeEChamaOOrquestrador() {
        consumidor.tratar(CORPO);

        assertThat(processador.contextos).singleElement().satisfies(c -> {
            assertThat(c.messageId()).isEqualTo("m-9");
            assertThat(c.sagaId()).isEqualTo("s-9");
            assertThat(c.osId()).isEqualTo(5L);
        });
        assertThat(processador.eventos).singleElement().isEqualTo(new EventoSaga.PagamentoAprovado("p-1", "o-1"));
    }

    @Test
    void corpoQueNaoEJsonEMensagemInvalida() {
        assertThatThrownBy(() -> consumidor.tratar("isto não é json")).isInstanceOf(MensagemInvalidaException.class);
        assertThat(processador.eventos).isEmpty();
    }

    @Test
    void erroDoOrquestradorSobeParaAMensagemNaoSerConfirmada() {
        processador.falha = new IllegalStateException("banco fora");

        assertThatThrownBy(() -> consumidor.tratar(CORPO)).hasMessage("banco fora");
    }
}
