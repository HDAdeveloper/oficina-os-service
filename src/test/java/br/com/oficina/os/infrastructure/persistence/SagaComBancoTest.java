package br.com.oficina.os.infrastructure.persistence;

import br.com.oficina.os.application.port.in.ContextoMensagem;
import br.com.oficina.os.application.port.in.EventoSaga;
import br.com.oficina.os.application.port.in.GestaoClientes;
import br.com.oficina.os.application.port.in.GestaoClientes.NovoCliente;
import br.com.oficina.os.application.port.in.GestaoOrdensServico;
import br.com.oficina.os.application.port.in.GestaoOrdensServico.AberturaOS;
import br.com.oficina.os.application.port.in.GestaoOrdensServico.ItemSolicitado;
import br.com.oficina.os.application.port.in.GestaoPecas;
import br.com.oficina.os.application.port.in.GestaoPecas.NovaPeca;
import br.com.oficina.os.application.port.in.GestaoServicos;
import br.com.oficina.os.application.port.in.GestaoServicos.NovoServico;
import br.com.oficina.os.application.port.in.GestaoVeiculos;
import br.com.oficina.os.application.port.in.GestaoVeiculos.NovoVeiculo;
import br.com.oficina.os.application.port.in.ProcessadorEventos;
import br.com.oficina.os.domain.model.EstadoSaga;
import br.com.oficina.os.domain.model.OrdemServico;
import br.com.oficina.os.domain.model.Saga;
import br.com.oficina.os.domain.model.StatusOS;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Orquestrador + PostgreSQL real + outbox: a Saga inteira gravando estado, OS e comandos juntos. */
@SpringBootTest
class SagaComBancoTest extends PostgresTestBase {

    private static final AtomicInteger SEQ = new AtomicInteger(1000);

    @Autowired GestaoClientes clientes;
    @Autowired GestaoVeiculos veiculos;
    @Autowired GestaoServicos servicos;
    @Autowired GestaoPecas pecas;
    @Autowired GestaoOrdensServico ordens;
    @Autowired ProcessadorEventos eventos;
    @Autowired OutboxComandoJpa outbox;
    @Autowired ObjectMapper json;

    /** OS em diagnóstico com 1 serviço (150,00) e 2 peças (35,00 cada) = 220,00. */
    private Long osEmDiagnostico() {
        int n = SEQ.incrementAndGet();
        var cliente = clientes.cadastrar(new NovoCliente("Cliente " + n, CpfDeTeste.gerar(n), null, null, null));
        veiculos.cadastrar(new NovoVeiculo(placa(n), "VW", "Gol", 2020, cliente.id()));
        var servico = servicos.cadastrar(new NovoServico("Alinhamento " + n, null, new BigDecimal("150.00"), 30));
        var peca = pecas.cadastrar(new NovaPeca("SAG-" + n, "Filtro " + n, null, new BigDecimal("35.00")));
        OrdemServico os = ordens.abrir(new AberturaOS(CpfDeTeste.gerar(n), placa(n), "Revisão", null,
                List.of(new ItemSolicitado(servico.id(), 1)), List.of(new ItemSolicitado(peca.id(), 2))));
        ordens.iniciarDiagnostico(os.getId());
        return os.getId();
    }

    private static String placa(int n) {
        return String.format("SGX%dA%02d", n % 10, n % 100);
    }

    private Saga saga(Long osId) {
        return ordens.consultarSagas(osId).get(0);
    }

    private void evento(Long osId, EventoSaga e) {
        eventos.processar(new ContextoMensagem(UUID.randomUUID().toString(), saga(osId).getId(), osId, "corr"), e);
    }

    private List<String> tipos(Long osId) {
        return outbox.findByOsIdOrderByIdAsc(osId).stream().map(OutboxComandoEntity::getTipo).toList();
    }

    private JsonNode payload(Long osId, String tipo) throws Exception {
        var linha = outbox.findByOsIdOrderByIdAsc(osId).stream().filter(e -> e.getTipo().equals(tipo)).findFirst()
                .orElseThrow();
        return json.readTree(linha.getPayload());
    }

    @Test
    void fluxoFelizGravaOsSagaEComandosNoBanco() throws Exception {
        Long osId = osEmDiagnostico();

        ordens.solicitarOrcamento(osId);
        assertThat(saga(osId).getEstado()).isEqualTo(EstadoSaga.ORCAMENTO_SOLICITADO);
        assertThat(saga(osId).getCorrelationId()).isNotBlank();

        evento(osId, new EventoSaga.OrcamentoCriado("orc-1", new BigDecimal("220.00")));
        assertThat(ordens.buscarPorId(osId).getStatus()).isEqualTo(StatusOS.AGUARDANDO_APROVACAO);

        ordens.aprovarOrcamento(osId, "APRO");
        evento(osId, new EventoSaga.PagamentoAprovado("pag-1", "orc-1"));
        evento(osId, new EventoSaga.EstoqueReservado("res-1"));
        assertThat(ordens.buscarPorId(osId).getStatus()).isEqualTo(StatusOS.EM_EXECUCAO);
        evento(osId, new EventoSaga.ExecucaoFinalizada());

        assertThat(ordens.buscarPorId(osId).getStatus()).isEqualTo(StatusOS.FINALIZADA);
        assertThat(saga(osId).getEstado()).isEqualTo(EstadoSaga.CONCLUIDA);
        assertThat(tipos(osId)).containsExactly("CRIAR_ORCAMENTO", "COBRAR_ORCAMENTO", "RESERVAR_ESTOQUE");

        JsonNode criar = payload(osId, "CRIAR_ORCAMENTO");
        assertThat(criar.get("valorTotal").decimalValue()).isEqualByComparingTo("220.00");
        assertThat(criar.get("itens")).hasSize(2);
        JsonNode cobrar = payload(osId, "COBRAR_ORCAMENTO");
        assertThat(cobrar.get("orcamentoId").asText()).isEqualTo("orc-1");
        assertThat(cobrar.get("metodo").asText()).isEqualTo("APRO");
        JsonNode reservar = payload(osId, "RESERVAR_ESTOQUE");
        assertThat(reservar.get("pecas").get(0).get("quantidade").asInt()).isEqualTo(2);
        assertThat(reservar.get("servicos").get(0).asText()).startsWith("Alinhamento");
        assertThat(outbox.findByOsIdOrderByIdAsc(osId)).allSatisfy(e -> {
            assertThat(e.getPublicadoEm()).isNull();
            assertThat(e.getSagaId()).isEqualTo(saga(osId).getId());
        });
        assertThat(outbox.findByOsIdOrderByIdAsc(osId)).extracting(OutboxComandoEntity::getDestino)
                .containsExactly("billing", "billing", "execution");
    }

    @Test
    void compensacaoCLiberaOEstoqueEEstornaGravandoCadaComando() {
        Long osId = osEmDiagnostico();
        ordens.solicitarOrcamento(osId);
        evento(osId, new EventoSaga.OrcamentoCriado("orc-1", new BigDecimal("220.00")));
        ordens.aprovarOrcamento(osId);
        evento(osId, new EventoSaga.PagamentoAprovado("pag-1", "orc-1"));
        evento(osId, new EventoSaga.EstoqueReservado("res-1"));

        evento(osId, new EventoSaga.ExecucaoFalhou("motor travou"));
        evento(osId, new EventoSaga.EstoqueLiberado());
        evento(osId, new EventoSaga.PagamentoEstornado("pag-1"));

        assertThat(ordens.buscarPorId(osId).getStatus()).isEqualTo(StatusOS.CANCELADA);
        assertThat(saga(osId).getEstado()).isEqualTo(EstadoSaga.CANCELADA);
        assertThat(tipos(osId)).containsExactly("CRIAR_ORCAMENTO", "COBRAR_ORCAMENTO", "RESERVAR_ESTOQUE",
                "LIBERAR_ESTOQUE", "ESTORNAR_PAGAMENTO");
    }

    @Test
    void mensagemRepetidaNaoGeraNovoComandoNemMudaOEstado() {
        Long osId = osEmDiagnostico();
        ordens.solicitarOrcamento(osId);
        ContextoMensagem ctx = new ContextoMensagem("msg-fixa-" + osId, saga(osId).getId(), osId, "corr");
        eventos.processar(ctx, new EventoSaga.OrcamentoCriado("orc-1", new BigDecimal("220.00")));
        ordens.aprovarOrcamento(osId);
        ContextoMensagem pagamento = new ContextoMensagem("msg-pag-" + osId, saga(osId).getId(), osId, "corr");
        eventos.processar(pagamento, new EventoSaga.PagamentoAprovado("pag-1", "orc-1"));

        eventos.processar(pagamento, new EventoSaga.PagamentoAprovado("pag-1", "orc-1"));
        eventos.processar(ctx, new EventoSaga.OrcamentoCriado("orc-1", new BigDecimal("220.00")));

        assertThat(tipos(osId)).containsExactly("CRIAR_ORCAMENTO", "COBRAR_ORCAMENTO", "RESERVAR_ESTOQUE");
        assertThat(saga(osId).getEstado()).isEqualTo(EstadoSaga.RESERVANDO_ESTOQUE);
    }

    @Test
    void semPrazoVencidoNadaEhReenviado() {
        Long osId = osEmDiagnostico();
        ordens.solicitarOrcamento(osId);

        assertThat(eventos.verificarPrazos()).isZero();
        assertThat(tipos(osId)).containsExactly("CRIAR_ORCAMENTO");
    }
}
