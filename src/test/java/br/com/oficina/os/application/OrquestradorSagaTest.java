package br.com.oficina.os.application;

import br.com.oficina.os.application.port.in.ContextoMensagem;
import br.com.oficina.os.application.port.in.EventoSaga;
import br.com.oficina.os.application.port.in.EventoSaga.EstoqueInsuficiente;
import br.com.oficina.os.application.port.in.EventoSaga.EstoqueLiberado;
import br.com.oficina.os.application.port.in.EventoSaga.EstoqueReservado;
import br.com.oficina.os.application.port.in.EventoSaga.ExecucaoFalhou;
import br.com.oficina.os.application.port.in.EventoSaga.ExecucaoFinalizada;
import br.com.oficina.os.application.port.in.EventoSaga.ExecucaoIniciada;
import br.com.oficina.os.application.port.in.EventoSaga.LinkPagamentoGerado;
import br.com.oficina.os.application.port.in.EventoSaga.OrcamentoCancelado;
import br.com.oficina.os.application.port.in.EventoSaga.OrcamentoCriado;
import br.com.oficina.os.application.port.in.EventoSaga.OrcamentoFalhou;
import br.com.oficina.os.application.port.in.EventoSaga.PagamentoAprovado;
import br.com.oficina.os.application.port.in.EventoSaga.PagamentoEstornado;
import br.com.oficina.os.application.port.in.EventoSaga.PagamentoRecusado;
import br.com.oficina.os.application.port.in.GestaoOrdensServico.AberturaOS;
import br.com.oficina.os.application.port.in.GestaoOrdensServico.ItemSolicitado;
import br.com.oficina.os.application.service.ClienteService;
import br.com.oficina.os.application.service.OrdemServicoService;
import br.com.oficina.os.application.service.OrquestradorSaga;
import br.com.oficina.os.application.service.PecaService;
import br.com.oficina.os.application.service.ServicoService;
import br.com.oficina.os.application.service.VeiculoService;
import br.com.oficina.os.application.port.in.GestaoClientes.NovoCliente;
import br.com.oficina.os.application.port.in.GestaoPecas.NovaPeca;
import br.com.oficina.os.application.port.in.GestaoServicos.NovoServico;
import br.com.oficina.os.application.port.in.GestaoVeiculos.NovoVeiculo;
import br.com.oficina.os.domain.exception.RegraDeNegocioException;
import br.com.oficina.os.domain.model.EstadoSaga;
import br.com.oficina.os.domain.model.OrdemServico;
import br.com.oficina.os.domain.model.Saga;
import br.com.oficina.os.domain.model.StatusOS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A Saga inteira com repositórios em memória: fluxo feliz, compensações A/B/C, repetições e prazos. */
class OrquestradorSagaTest {

    /** Relógio que anda quando o teste manda. */
    static class RelogioMutavel extends Clock {
        Instant agora = Instant.parse("2026-10-23T12:00:00Z");

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return agora; }
        void avancar(Duration d) { agora = agora.plus(d); }
    }

    private final RelogioMutavel relogio = new RelogioMutavel();
    private final AtomicInteger seq = new AtomicInteger();

    private Fakes.OrdensMem ordens;
    private Fakes.ClientesMem clientes;
    private Fakes.SagasMem sagas;
    private Fakes.SagaGravador comandos;
    private OrquestradorSaga orquestrador;
    private OrdemServicoService service;
    private Long osId;

    @BeforeEach
    void setUp() {
        ordens = new Fakes.OrdensMem();
        clientes = new Fakes.ClientesMem();
        var veiculos = new Fakes.VeiculosMem();
        var servicos = new Fakes.ServicosMem();
        var pecas = new Fakes.PecasMem();
        sagas = new Fakes.SagasMem();
        comandos = new Fakes.SagaGravador();
        orquestrador = new OrquestradorSaga(sagas, new Fakes.ProcessadasMem(), ordens, clientes, comandos,
                new Fakes.IdsSequenciais(), new Fakes.CorrelacaoFixa(), relogio, Duration.ofSeconds(60), 2);
        service = new OrdemServicoService(ordens, clientes, veiculos, servicos, pecas, new Fakes.GeradorFixo(),
                orquestrador, relogio);

        var cliente = new ClienteService(clientes)
                .cadastrar(new NovoCliente("Ana Souza", "529.982.247-25", "ana@exemplo.com", null, null));
        new VeiculoService(veiculos, clientes).cadastrar(new NovoVeiculo("ABC1D23", "Chevrolet", "Onix", 2022,
                cliente.id()));
        var servico = new ServicoService(servicos)
                .cadastrar(new NovoServico("Troca de óleo", null, new BigDecimal("150.00"), 30));
        var peca = new PecaService(pecas)
                .cadastrar(new NovaPeca("FLT-01", "Filtro de óleo", null, new BigDecimal("35.00")));

        OrdemServico os = service.abrir(new AberturaOS("52998224725", "ABC1D23", "Revisão", null,
                List.of(new ItemSolicitado(servico.id(), 1)), List.of(new ItemSolicitado(peca.id(), 2))));
        osId = os.getId();
        service.iniciarDiagnostico(osId);
    }

    // ------------------------------------------------------------ apoio

    private Saga saga() {
        return sagas.buscarPorOs(osId).get(0);
    }

    private OrdemServico os() {
        return ordens.buscarPorId(osId).orElseThrow();
    }

    private void evento(EventoSaga e) {
        eventoComId("m-" + seq.incrementAndGet(), e);
    }

    private void eventoComId(String messageId, EventoSaga e) {
        orquestrador.processar(new ContextoMensagem(messageId, saga().getId(), osId, "corr-teste"), e);
    }

    /** Leva a Saga até o ponto pedido, passando pelo fluxo feliz. */
    private void ate(EstadoSaga alvo) {
        service.solicitarOrcamento(osId);
        if (alvo == EstadoSaga.ORCAMENTO_SOLICITADO) return;
        evento(new OrcamentoCriado("orc-1", new BigDecimal("220.00")));
        if (alvo == EstadoSaga.AGUARDANDO_APROVACAO) return;
        service.aprovarOrcamento(osId, "APRO");
        if (alvo == EstadoSaga.COBRANDO) return;
        evento(new PagamentoAprovado("pag-1", "orc-1"));
        if (alvo == EstadoSaga.RESERVANDO_ESTOQUE) return;
        evento(new EstoqueReservado("res-1"));
    }

    // ------------------------------------------------------------ fluxo feliz

    @Test
    void fluxoFelizDoOrcamentoAteAFinalizacao() {
        service.solicitarOrcamento(osId);
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.ORCAMENTO_SOLICITADO);
        assertThat(saga().getCorrelationId()).isEqualTo("corr-teste");

        evento(new OrcamentoCriado("orc-1", new BigDecimal("220.00")));
        assertThat(os().getStatus()).isEqualTo(StatusOS.AGUARDANDO_APROVACAO);
        assertThat(os().getOrcamentoId()).isEqualTo("orc-1");
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.AGUARDANDO_APROVACAO);

        service.aprovarOrcamento(osId, "APRO");
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.COBRANDO);
        assertThat(saga().getMetodoPagamento()).isEqualTo("APRO");

        evento(new PagamentoAprovado("pag-1", "orc-1"));
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.RESERVANDO_ESTOQUE);
        assertThat(saga().getPagamentoId()).isEqualTo("pag-1");
        assertThat(os().getStatus()).isEqualTo(StatusOS.AGUARDANDO_APROVACAO); // só muda com a reserva

        evento(new EstoqueReservado("res-1"));
        assertThat(os().getStatus()).isEqualTo(StatusOS.EM_EXECUCAO);
        assertThat(saga().getReservaId()).isEqualTo("res-1");

        evento(new ExecucaoIniciada());
        assertThat(os().getStatus()).isEqualTo(StatusOS.EM_EXECUCAO);
        assertThat(os().getHistorico()).anyMatch(h -> h.motivo().contains("Reparo iniciado"));

        evento(new ExecucaoFinalizada());
        assertThat(os().getStatus()).isEqualTo(StatusOS.FINALIZADA);
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.CONCLUIDA);
        assertThat(comandos.tipos()).containsExactly("CRIAR_ORCAMENTO", "COBRAR_ORCAMENTO", "RESERVAR_ESTOQUE");
    }

    @Test
    void orcamentoFalhouMantemAOsEmDiagnosticoEPermiteNovaTentativa() {
        service.solicitarOrcamento(osId);

        evento(new OrcamentoFalhou("itens inválidos"));

        assertThat(os().getStatus()).isEqualTo(StatusOS.EM_DIAGNOSTICO);
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.ORCAMENTO_FALHOU);
        assertThat(os().getHistorico()).anyMatch(h -> h.motivo().contains("itens inválidos"));

        service.solicitarOrcamento(osId);
        assertThat(sagas.buscarPorOs(osId)).hasSize(2);
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.ORCAMENTO_SOLICITADO);
    }

    @Test
    void pedirOrcamentoDeNovoComASagaAbertaReaproveitaASaga() {
        service.solicitarOrcamento(osId);
        String id = saga().getId();

        service.solicitarOrcamento(osId);

        assertThat(sagas.buscarPorOs(osId)).hasSize(1);
        assertThat(saga().getId()).isEqualTo(id);
        assertThat(comandos.tipos()).containsExactly("CRIAR_ORCAMENTO", "CRIAR_ORCAMENTO");
    }

    // ------------------------------------------------------------ compensação A

    @Test
    void pagamentoRecusadoCancelaOOrcamentoEVoltaAoDiagnostico() {
        ate(EstadoSaga.COBRANDO);

        evento(new PagamentoRecusado("orc-1", "saldo insuficiente"));

        assertThat(os().getStatus()).isEqualTo(StatusOS.EM_DIAGNOSTICO);
        assertThat(os().getOrcamentoId()).isNull();
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.CANCELANDO_ORCAMENTO);
        assertThat(comandos.comandos).last().isEqualTo("CANCELAR_ORCAMENTO:" + osId + ":orc-1:saldo insuficiente");

        evento(new OrcamentoCancelado());
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.ENCERRADA);
    }

    @Test
    void clienteRecusaOOrcamentoEASagaEncerraQuandoOBillingConfirma() {
        ate(EstadoSaga.AGUARDANDO_APROVACAO);

        service.recusarOrcamento(osId, "Achei caro");

        assertThat(os().getStatus()).isEqualTo(StatusOS.EM_DIAGNOSTICO);
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.CANCELANDO_ORCAMENTO);
        evento(new OrcamentoCancelado());
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.ENCERRADA);
    }

    // ------------------------------------------------------------ compensação B

    @Test
    void estoqueInsuficienteEstornaOPagamentoECancelaAOs() {
        ate(EstadoSaga.RESERVANDO_ESTOQUE);

        evento(new EstoqueInsuficiente("FLT-01 (pedido 2, disponível 0)"));

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.ESTORNANDO);
        assertThat(comandos.comandos).last()
                .isEqualTo("ESTORNAR_PAGAMENTO:" + osId + ":pag-1:Estoque insuficiente: FLT-01 (pedido 2, disponível 0)");
        assertThat(os().getStatus()).isEqualTo(StatusOS.AGUARDANDO_APROVACAO); // ainda não cancelou

        evento(new PagamentoEstornado("pag-1"));

        assertThat(os().getStatus()).isEqualTo(StatusOS.CANCELADA);
        assertThat(os().getMotivoCancelamento()).contains("Estoque insuficiente");
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.CANCELADA);
    }

    // ------------------------------------------------------------ compensação C

    @Test
    void falhaNaExecucaoLiberaOEstoqueDepoisEstorna() {
        ate(EstadoSaga.EM_EXECUCAO);

        evento(new ExecucaoFalhou("motor travou"));
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.LIBERANDO_ESTOQUE);
        assertThat(comandos.tipos()).last().isEqualTo("LIBERAR_ESTOQUE");

        evento(new EstoqueLiberado());
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.ESTORNANDO);
        assertThat(comandos.tipos()).last().isEqualTo("ESTORNAR_PAGAMENTO");
        assertThat(os().getStatus()).isEqualTo(StatusOS.EM_EXECUCAO);

        evento(new PagamentoEstornado("pag-1"));
        assertThat(os().getStatus()).isEqualTo(StatusOS.CANCELADA);
        assertThat(os().getMotivoCancelamento()).contains("motor travou");
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.CANCELADA);
        // ordem inversa dos passos concluídos: libera o estoque antes de estornar
        assertThat(comandos.tipos()).containsExactly("CRIAR_ORCAMENTO", "COBRAR_ORCAMENTO", "RESERVAR_ESTOQUE",
                "LIBERAR_ESTOQUE", "ESTORNAR_PAGAMENTO");
    }

    // ------------------------------------------------------------ idempotência e eventos fora de hora

    @Test
    void mensagemRepetidaNaoEhTratadaDeNovo() {
        ate(EstadoSaga.COBRANDO);
        eventoComId("m-fixo", new PagamentoAprovado("pag-1", "orc-1"));
        int comandosAntes = comandos.comandos.size();

        eventoComId("m-fixo", new PagamentoAprovado("pag-1", "orc-1"));

        assertThat(comandos.comandos).hasSize(comandosAntes);
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.RESERVANDO_ESTOQUE);
    }

    @Test
    void eventoQueNaoCabeNoPassoEhRegistradoEIgnorado() {
        ate(EstadoSaga.AGUARDANDO_APROVACAO);

        evento(new PagamentoEstornado("pag-9"));
        evento(new ExecucaoFinalizada());

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.AGUARDANDO_APROVACAO);
        assertThat(os().getStatus()).isEqualTo(StatusOS.AGUARDANDO_APROVACAO);
        assertThat(saga().getPassos()).filteredOn(p -> p.tipo().equals("EVENTO_IGNORADO")).hasSize(2);
    }

    @Test
    void respostaDuplicadaComOutroMessageIdNaoAvancaDuasVezes() {
        ate(EstadoSaga.COBRANDO);
        evento(new PagamentoAprovado("pag-1", "orc-1"));
        int antes = comandos.comandos.size();

        evento(new PagamentoAprovado("pag-1", "orc-1")); // o Billing respondeu de novo ao reenvio

        assertThat(comandos.comandos).hasSize(antes);
    }

    @Test
    void eventoDeSagaDesconhecidaOuDeOutraOsEhIgnorado() {
        ate(EstadoSaga.AGUARDANDO_APROVACAO);

        orquestrador.processar(new ContextoMensagem("x1", "saga-nao-existe", osId, "c"), new OrcamentoCancelado());
        orquestrador.processar(new ContextoMensagem("x2", saga().getId(), 999L, "c"), new OrcamentoCancelado());

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.AGUARDANDO_APROVACAO);
    }

    @Test
    void aprovarSemSagaAguardandoAprovacaoFalha() {
        assertThatThrownBy(() -> orquestrador.cobrar(os(), "APRO")).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void cancelamentoManualEncerraASagaQueEsperavaOOrcamento() {
        service.solicitarOrcamento(osId);

        service.cancelar(osId, "desistiu");

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.ENCERRADA);
        evento(new OrcamentoCriado("orc-tardio", new BigDecimal("220.00"))); // resposta que chegou atrasada
        assertThat(os().getStatus()).isEqualTo(StatusOS.CANCELADA);
    }

    // ------------------------------------------------------------ painel

    @Test
    void sagasRecentesVemDaMaisNovaParaAMaisAntigaELimitamAoIntervaloPermitido() {
        service.solicitarOrcamento(osId);
        relogio.avancar(Duration.ofMinutes(1));
        evento(new OrcamentoCriado("orc-1", new BigDecimal("220.00"))); // mexe na Saga: ela fica a mais recente

        assertThat(orquestrador.sagasRecentes(10)).extracting(Saga::getId).containsExactly(saga().getId());
        assertThat(orquestrador.sagasRecentes(0)).hasSize(1);       // piso 1
        assertThat(orquestrador.sagasRecentes(100000)).hasSize(1);  // teto 100
    }

    // ------------------------------------------------------------ pagamento por link

    private LinkPagamentoGerado link(Duration validade) {
        return new LinkPagamentoGerado("pag-1", "https://mp/pagar/1", relogio.agora.plus(validade));
    }

    @Test
    void linkGeradoColocaASagaParaEsperarOClienteComPrazoDaValidade() {
        ate(EstadoSaga.COBRANDO);

        evento(link(Duration.ofHours(1)));

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.AGUARDANDO_PAGAMENTO);
        assertThat(saga().getLinkPagamento()).isEqualTo("https://mp/pagar/1");
        assertThat(saga().getPagamentoId()).isEqualTo("pag-1");
        // validade do link + 60 s de folga para o aviso do Billing chegar antes do prazo
        assertThat(saga().getPrazo()).isEqualTo(relogio.agora.plus(Duration.ofHours(1)).plusSeconds(60));
        assertThat(saga().getTentativas()).isZero();
        assertThat(os().getStatus()).isEqualTo(StatusOS.AGUARDANDO_APROVACAO);
    }

    @Test
    void linkSemValidadeInformadaUsaUmDiaPorPadrao() {
        ate(EstadoSaga.COBRANDO);

        evento(new LinkPagamentoGerado("pag-1", "https://mp/pagar/1", null));

        assertThat(saga().getPrazo()).isEqualTo(relogio.agora.plus(Duration.ofHours(24)).plusSeconds(60));
    }

    @Test
    void pagamentoAprovadoDepoisDoLinkSegueParaAReservaDoEstoque() {
        ate(EstadoSaga.COBRANDO);
        evento(link(Duration.ofHours(1)));

        evento(new PagamentoAprovado("pag-1", "orc-1"));

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.RESERVANDO_ESTOQUE);
        assertThat(comandos.tipos()).last().isEqualTo("RESERVAR_ESTOQUE");
    }

    @Test
    void linkVencidoNoBillingRodaACompensacaoA() {
        ate(EstadoSaga.COBRANDO);
        evento(link(Duration.ofHours(1)));

        evento(new EventoSaga.PagamentoRecusado("orc-1", "Link de pagamento expirou sem pagamento"));

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.CANCELANDO_ORCAMENTO);
        assertThat(os().getStatus()).isEqualTo(StatusOS.EM_DIAGNOSTICO);
        assertThat(comandos.tipos()).last().isEqualTo("CANCELAR_ORCAMENTO");
    }

    @Test
    void semAvisoDoBillingOPrazoDoLinkCompensaSemReenviarACobranca() {
        ate(EstadoSaga.COBRANDO);
        evento(link(Duration.ofHours(1)));
        int comandosAntes = comandos.comandos.size();

        relogio.avancar(Duration.ofMinutes(30)); // o cliente ainda pode pagar: nada acontece
        assertThat(orquestrador.verificarPrazos()).isZero();
        relogio.avancar(Duration.ofMinutes(32)); // passou a validade e a folga
        assertThat(orquestrador.verificarPrazos()).isEqualTo(1);

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.CANCELANDO_ORCAMENTO);
        assertThat(os().getStatus()).isEqualTo(StatusOS.EM_DIAGNOSTICO);
        assertThat(comandos.comandos).hasSize(comandosAntes + 1);
        assertThat(comandos.tipos()).last().isEqualTo("CANCELAR_ORCAMENTO");
        assertThat(saga().getMotivo()).contains("Link de pagamento expirou");
    }

    @Test
    void linkForaDoPassoDeCobrancaEhIgnorado() {
        ate(EstadoSaga.AGUARDANDO_APROVACAO);

        evento(link(Duration.ofHours(1)));

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.AGUARDANDO_APROVACAO);
        assertThat(saga().getLinkPagamento()).isNull();
    }

    @Test
    void linkRepetidoNaoReiniciaAEspera() {
        ate(EstadoSaga.COBRANDO);
        evento(link(Duration.ofHours(1)));
        var prazo = saga().getPrazo();

        evento(link(Duration.ofHours(5))); // reenvio do Billing quando a cobrança foi repetida

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.AGUARDANDO_PAGAMENTO);
        assertThat(saga().getPrazo()).isEqualTo(prazo);
    }

    @Test
    void clienteNaoRecusaOOrcamentoEnquantoOLinkEstaAberto() {
        ate(EstadoSaga.COBRANDO);
        evento(link(Duration.ofHours(1)));

        assertThatThrownBy(() -> service.recusarOrcamento(osId, "mudei de ideia"))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    // ------------------------------------------------------------ prazos

    @Test
    void semRespostaOComandoEhReenviadoDepoisDoPrazo() {
        service.solicitarOrcamento(osId);
        assertThat(orquestrador.verificarPrazos()).isZero(); // ainda dentro do prazo

        relogio.avancar(Duration.ofSeconds(61));
        assertThat(orquestrador.verificarPrazos()).isEqualTo(1);

        assertThat(comandos.tipos()).containsExactly("CRIAR_ORCAMENTO", "CRIAR_ORCAMENTO");
        assertThat(saga().getTentativas()).isEqualTo(1);
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.ORCAMENTO_SOLICITADO);
    }

    @Test
    void orcamentoSemRespostaDepoisDasTentativasFalha() {
        service.solicitarOrcamento(osId);
        for (int i = 0; i < 3; i++) {
            relogio.avancar(Duration.ofSeconds(61));
            orquestrador.verificarPrazos();
        }

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.ORCAMENTO_FALHOU);
        assertThat(os().getStatus()).isEqualTo(StatusOS.EM_DIAGNOSTICO);
        assertThat(comandos.tipos()).hasSize(3); // original + 2 reenvios
    }

    @Test
    void cobrancaSemRespostaCompensaComoPagamentoRecusado() {
        ate(EstadoSaga.COBRANDO);
        for (int i = 0; i < 3; i++) {
            relogio.avancar(Duration.ofSeconds(61));
            orquestrador.verificarPrazos();
        }

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.CANCELANDO_ORCAMENTO);
        assertThat(os().getStatus()).isEqualTo(StatusOS.EM_DIAGNOSTICO);
        assertThat(comandos.tipos()).last().isEqualTo("CANCELAR_ORCAMENTO");
    }

    @Test
    void reservaSemRespostaCompensaComoEstoqueInsuficiente() {
        ate(EstadoSaga.RESERVANDO_ESTOQUE);
        for (int i = 0; i < 3; i++) {
            relogio.avancar(Duration.ofSeconds(61));
            orquestrador.verificarPrazos();
        }

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.ESTORNANDO);
        assertThat(comandos.tipos()).last().isEqualTo("ESTORNAR_PAGAMENTO");
    }

    @Test
    void estornoSemConfirmacaoFicaPendenteEPodeSerRetomado() {
        ate(EstadoSaga.EM_EXECUCAO);
        evento(new ExecucaoFalhou("x"));
        evento(new EstoqueLiberado());
        for (int i = 0; i < 3; i++) {
            relogio.avancar(Duration.ofSeconds(61));
            orquestrador.verificarPrazos();
        }

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.ESTORNO_PENDENTE);
        assertThat(saga().getPrazo()).isNull();
        assertThat(os().getStatus()).isEqualTo(StatusOS.EM_EXECUCAO);

        evento(new PagamentoEstornado("pag-1")); // a operação reprocessou a DLQ
        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.CANCELADA);
        assertThat(os().getStatus()).isEqualTo(StatusOS.CANCELADA);
    }

    @Test
    void liberacaoSemConfirmacaoFicaPendente() {
        ate(EstadoSaga.EM_EXECUCAO);
        evento(new ExecucaoFalhou("x"));
        for (int i = 0; i < 3; i++) {
            relogio.avancar(Duration.ofSeconds(61));
            orquestrador.verificarPrazos();
        }

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.LIBERACAO_PENDENTE);
    }

    @Test
    void cancelamentoDeOrcamentoSemConfirmacaoEncerra() {
        ate(EstadoSaga.AGUARDANDO_APROVACAO);
        service.recusarOrcamento(osId, "caro");
        for (int i = 0; i < 3; i++) {
            relogio.avancar(Duration.ofSeconds(61));
            orquestrador.verificarPrazos();
        }

        assertThat(saga().getEstado()).isEqualTo(EstadoSaga.ENCERRADA);
    }

    @Test
    void passoSemPrazoNaoEhReenviado() {
        ate(EstadoSaga.EM_EXECUCAO);

        relogio.avancar(Duration.ofDays(2));

        assertThat(orquestrador.verificarPrazos()).isZero();
    }

    @Test
    void diarioDaSagaRegistraComandosEventosEMudancasDeEstado() {
        ate(EstadoSaga.RESERVANDO_ESTOQUE);

        assertThat(saga().getPassos()).extracting(p -> p.tipo())
                .contains("SAGA_INICIADA", "EVENTO", "AGUARDANDO_APROVACAO", "COBRANDO", "RESERVANDO_ESTOQUE");
        assertThat(service.consultarSagas(osId)).hasSize(1);
        assertThat(orquestrador.sagaAtual(osId)).isPresent();
    }
}
