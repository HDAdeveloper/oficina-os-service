package br.com.oficina.os.infrastructure.mensageria;

import br.com.oficina.os.application.port.in.EventoSaga;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventoMapperTest {

    private final EventoMapper mapper = new EventoMapper();
    private final ObjectMapper json = new ObjectMapper();

    private EnvelopeMensagem env(String type, String payload) throws Exception {
        return new EnvelopeMensagem("m-1", "saga-1", 42L, type, 1, "2026-10-23T12:00:00Z", "corr-1",
                payload == null ? null : json.readTree(payload));
    }

    private EventoSaga evento(String type, String payload) throws Exception {
        return mapper.mapear(env(type, payload)).evento();
    }

    @Test
    void trazOContextoDoEnvelope() throws Exception {
        var m = mapper.mapear(env("ORCAMENTO_CANCELADO", null));

        assertThat(m.contexto().messageId()).isEqualTo("m-1");
        assertThat(m.contexto().sagaId()).isEqualTo("saga-1");
        assertThat(m.contexto().osId()).isEqualTo(42L);
        assertThat(m.contexto().correlationId()).isEqualTo("corr-1");
    }

    @Test
    void traduzOsEventosDoBilling() throws Exception {
        assertThat(evento("ORCAMENTO_CRIADO", "{\"orcamentoId\":\"o-1\",\"valorTotal\":220.50}"))
                .isEqualTo(new EventoSaga.OrcamentoCriado("o-1", new BigDecimal("220.5")));
        assertThat(evento("ORCAMENTO_CRIADO", "{\"orcamentoId\":\"o-1\",\"valorTotal\":\"10.00\"}"))
                .isEqualTo(new EventoSaga.OrcamentoCriado("o-1", new BigDecimal("10.00")));
        assertThat(evento("ORCAMENTO_FALHOU", "{\"motivo\":\"itens inválidos\"}"))
                .isEqualTo(new EventoSaga.OrcamentoFalhou("itens inválidos"));
        assertThat(evento("ORCAMENTO_CANCELADO", "{}")).isEqualTo(new EventoSaga.OrcamentoCancelado());
        assertThat(evento("PAGAMENTO_APROVADO", "{\"pagamentoId\":\"p-1\",\"orcamentoId\":\"o-1\"}"))
                .isEqualTo(new EventoSaga.PagamentoAprovado("p-1", "o-1"));
        assertThat(evento("PAGAMENTO_RECUSADO", "{\"orcamentoId\":\"o-1\",\"motivo\":\"saldo\"}"))
                .isEqualTo(new EventoSaga.PagamentoRecusado("o-1", "saldo"));
        assertThat(evento("PAGAMENTO_ESTORNADO", "{\"pagamentoId\":\"p-1\"}"))
                .isEqualTo(new EventoSaga.PagamentoEstornado("p-1"));
    }

    @Test
    void traduzOsEventosDoExecution() throws Exception {
        assertThat(evento("ESTOQUE_RESERVADO", "{\"reservaId\":\"r-1\"}"))
                .isEqualTo(new EventoSaga.EstoqueReservado("r-1"));
        assertThat(evento("ESTOQUE_LIBERADO", "{\"reservaId\":\"r-1\"}")).isEqualTo(new EventoSaga.EstoqueLiberado());
        assertThat(evento("ESTOQUE_LIBERADO", "{\"nadaALiberar\":true}")).isEqualTo(new EventoSaga.EstoqueLiberado());
        assertThat(evento("EXECUCAO_INICIADA", "{\"iniciadaEm\":\"2026-10-23T12:00:00Z\"}"))
                .isEqualTo(new EventoSaga.ExecucaoIniciada());
        assertThat(evento("EXECUCAO_FINALIZADA", "{}")).isEqualTo(new EventoSaga.ExecucaoFinalizada());
        assertThat(evento("EXECUCAO_FALHOU", "{\"motivo\":\"motor\"}")).isEqualTo(new EventoSaga.ExecucaoFalhou("motor"));
    }

    @Test
    void estoqueInsuficienteDescreveAsFaltas() throws Exception {
        var e = (EventoSaga.EstoqueInsuficiente) evento("ESTOQUE_INSUFICIENTE", """
                {"faltantes":[{"codigo":"FLT-01","solicitado":3,"disponivel":1},
                              {"codigo":"OLE-5W","solicitado":2,"disponivel":0}]}""");

        assertThat(e.descricao()).isEqualTo("FLT-01 (pedido 3, disponível 1); OLE-5W (pedido 2, disponível 0)");
    }

    @Test
    void estoqueInsuficienteDePedidoInvalidoTrazOMotivo() throws Exception {
        var e = (EventoSaga.EstoqueInsuficiente) evento("ESTOQUE_INSUFICIENTE",
                "{\"faltantes\":[],\"motivo\":\"Quantidade inválida\"}");
        var vazio = (EventoSaga.EstoqueInsuficiente) evento("ESTOQUE_INSUFICIENTE", "{}");

        assertThat(e.descricao()).isEqualTo("Quantidade inválida");
        assertThat(vazio.descricao()).isEqualTo("sem detalhes");
    }

    @Test
    void mensagemQueNuncaSeraEntendidaEInvalida() {
        assertThatThrownBy(() -> mapper.mapear(null)).isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> evento("ORCAMENTO_CRIADO", "{}")).isInstanceOf(MensagemInvalidaException.class)
                .hasMessageContaining("orcamentoId");
        assertThatThrownBy(() -> evento("ESTOQUE_RESERVADO", null)).isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> evento("PAGAMENTO_APROVADO", "{}")).isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> evento("ORCAMENTO_CRIADO", "{\"orcamentoId\":\"o\",\"valorTotal\":\"abc\"}"))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> evento("CRIAR_ORCAMENTO", "{}")).isInstanceOf(MensagemInvalidaException.class)
                .hasMessageContaining("CRIAR_ORCAMENTO");
        assertThatThrownBy(() -> evento(" ", "{}")).isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> mapper.mapear(new EnvelopeMensagem(null, "s", 1L, "ORCAMENTO_CANCELADO", 1, null,
                null, null))).isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> mapper.mapear(new EnvelopeMensagem("m", null, 1L, "ORCAMENTO_CANCELADO", 1, null,
                null, null))).isInstanceOf(MensagemInvalidaException.class);
    }
}
