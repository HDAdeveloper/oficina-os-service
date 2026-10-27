package br.com.oficina.os.application;

import br.com.oficina.os.application.port.in.GestaoClientes.NovoCliente;
import br.com.oficina.os.application.port.in.GestaoOrdensServico.AberturaOS;
import br.com.oficina.os.application.port.in.GestaoOrdensServico.ItemSolicitado;
import br.com.oficina.os.application.port.in.GestaoPecas.NovaPeca;
import br.com.oficina.os.application.port.in.GestaoServicos.NovoServico;
import br.com.oficina.os.application.port.in.GestaoVeiculos.NovoVeiculo;
import br.com.oficina.os.application.service.ClienteService;
import br.com.oficina.os.application.port.in.ContextoMensagem;
import br.com.oficina.os.application.port.in.EventoSaga;
import br.com.oficina.os.application.service.OrdemServicoService;
import br.com.oficina.os.application.service.OrquestradorSaga;
import br.com.oficina.os.application.service.PecaService;
import br.com.oficina.os.application.service.ServicoService;
import br.com.oficina.os.application.service.VeiculoService;
import br.com.oficina.os.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.os.domain.exception.RegraDeNegocioException;
import br.com.oficina.os.domain.model.Cliente;
import br.com.oficina.os.domain.model.OrdemServico;
import br.com.oficina.os.domain.model.Peca;
import br.com.oficina.os.domain.model.Saga;
import br.com.oficina.os.domain.model.Servico;
import br.com.oficina.os.domain.model.StatusOS;
import br.com.oficina.os.domain.model.Veiculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrdemServicoServiceTest {

    private static final String CPF = "529.982.247-25";
    private static final String PLACA = "ABC1D23";

    private final Clock relogio = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);

    private Fakes.ClientesMem clientesRepo;
    private Fakes.VeiculosMem veiculosRepo;
    private Fakes.ServicosMem servicosRepo;
    private Fakes.PecasMem pecasRepo;
    private Fakes.OrdensMem ordensRepo;
    private Fakes.SagaGravador saga;
    private Fakes.SagasMem sagasRepo;
    private OrquestradorSaga orquestrador;
    private OrdemServicoService service;

    private Cliente cliente;
    private Veiculo veiculo;
    private Servico servico;
    private Peca peca;

    @BeforeEach
    void setUp() {
        clientesRepo = new Fakes.ClientesMem();
        veiculosRepo = new Fakes.VeiculosMem();
        servicosRepo = new Fakes.ServicosMem();
        pecasRepo = new Fakes.PecasMem();
        ordensRepo = new Fakes.OrdensMem();
        saga = new Fakes.SagaGravador();
        sagasRepo = new Fakes.SagasMem();
        orquestrador = new OrquestradorSaga(sagasRepo, new Fakes.ProcessadasMem(), ordensRepo, clientesRepo, saga,
                new Fakes.IdsSequenciais(), new Fakes.CorrelacaoFixa(), relogio, Duration.ofSeconds(60), 3);
        service = new OrdemServicoService(ordensRepo, clientesRepo, veiculosRepo, servicosRepo, pecasRepo,
                new Fakes.GeradorFixo(), orquestrador, relogio);

        cliente = new ClienteService(clientesRepo)
                .cadastrar(new NovoCliente("Ana Souza", CPF, "ana@exemplo.com", null, null));
        veiculo = new VeiculoService(veiculosRepo, clientesRepo)
                .cadastrar(new NovoVeiculo(PLACA, "Chevrolet", "Onix", 2022, cliente.id()));
        servico = new ServicoService(servicosRepo)
                .cadastrar(new NovoServico("Troca de óleo", null, new BigDecimal("150.00"), 30));
        peca = new PecaService(pecasRepo)
                .cadastrar(new NovaPeca("FLT-01", "Filtro de óleo", null, new BigDecimal("35.00")));
    }

    private OrdemServico abrirSimples() {
        return service.abrir(new AberturaOS(CPF, PLACA, "Barulho no motor", null, List.of(), List.of()));
    }

    private OrdemServico abrirComItens() {
        return service.abrir(new AberturaOS("52998224725", "abc-1d23", "Revisão", "urgente",
                List.of(new ItemSolicitado(servico.id(), 1)),
                List.of(new ItemSolicitado(peca.id(), 2))));
    }

    /** Entrega ao orquestrador o ORCAMENTO_CRIADO que o Billing enviaria. */
    private void orcamentoCriado(Long osId, String orcamentoId) {
        Saga saga = sagasRepo.buscarPorOs(osId).get(0);
        orquestrador.processar(new ContextoMensagem("msg-" + orcamentoId, saga.getId(), osId, "corr-teste"),
                new EventoSaga.OrcamentoCriado(orcamentoId, ordensRepo.buscarPorId(osId).orElseThrow().valorTotal()));
    }

    // ------------------------------------------------------------ área do cliente

    @Test
    void clienteVeSoAsPropriasOsDaMaisRecenteParaAMaisAntiga() {
        OrdemServico primeira = abrirSimples();
        OrdemServico segunda = abrirSimples();

        assertThat(service.listarDoCliente("529.982.247-25")).extracting(OrdemServico::getId)
                .containsExactlyInAnyOrder(primeira.getId(), segunda.getId());
        assertThat(service.listarDoCliente(CPF)).hasSize(2);
    }

    @Test
    void clienteInexistenteNaAreaDoClienteDa404() {
        assertThatThrownBy(() -> service.listarDoCliente("11144477735"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // ------------------------------------------------------------ abertura

    @Test
    void abreOsComItensDoCatalogo() {
        OrdemServico os = abrirComItens();

        assertThat(os.getId()).isNotNull();
        assertThat(os.getNumero()).isEqualTo("OS-TESTE-1");
        assertThat(os.getStatus()).isEqualTo(StatusOS.RECEBIDA);
        assertThat(os.getClienteId()).isEqualTo(cliente.id());
        assertThat(os.getVeiculoId()).isEqualTo(veiculo.id());
        assertThat(os.valorTotal()).isEqualByComparingTo("220.00");
    }

    @Test
    void naoAbreOsParaClienteOuVeiculoInexistente() {
        assertThatThrownBy(() -> service.abrir(new AberturaOS("111.444.777-35", PLACA, "x", null, null, null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.abrir(new AberturaOS(CPF, "XYZ9A99", "x", null, null, null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void naoAbreOsParaClienteOuVeiculoInativo() {
        new VeiculoService(veiculosRepo, clientesRepo).desativar(veiculo.id());
        assertThatThrownBy(this::abrirSimples).isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Veículo inativo");

        new ClienteService(clientesRepo).desativar(cliente.id());
        assertThatThrownBy(this::abrirSimples).isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Cliente inativo");
    }

    @Test
    void naoAbreOsComVeiculoDeOutroCliente() {
        Cliente outro = new ClienteService(clientesRepo)
                .cadastrar(new NovoCliente("Bruno", "111.444.777-35", null, null, null));
        assertThatThrownBy(() -> service.abrir(new AberturaOS(outro.documento().numero(), PLACA, "x", null, null, null)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("não pertence");
    }

    @Test
    void naoAbreOsComItemInexistenteNoCatalogo() {
        assertThatThrownBy(() -> service.abrir(new AberturaOS(CPF, PLACA, "x", null,
                List.of(new ItemSolicitado(999L, 1)), null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.abrir(new AberturaOS(CPF, PLACA, "x", null,
                null, List.of(new ItemSolicitado(999L, 1)))))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // ------------------------------------------------------------ consultas

    @Test
    void consultaStatusEHistorico() {
        OrdemServico os = abrirSimples();
        service.iniciarDiagnostico(os.getId());

        assertThat(service.consultarStatus(os.getId())).isEqualTo(StatusOS.EM_DIAGNOSTICO);
        assertThat(service.consultarHistorico(os.getId())).hasSize(2);
        assertThat(service.buscarPorId(os.getId())).isSameAs(os);
        assertThatThrownBy(() -> service.buscarPorId(404L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void listaAtivasOrdenadasPorPrioridadeEDataDeAbertura() {
        OrdemServico recebida = abrirSimples();
        OrdemServico diagnostico = abrirSimples();
        service.iniciarDiagnostico(diagnostico.getId());
        OrdemServico cancelada = abrirSimples();
        service.cancelar(cancelada.getId(), "desistiu");

        List<OrdemServico> ativas = service.listarAtivas();

        assertThat(ativas).extracting(OrdemServico::getId)
                .containsExactly(diagnostico.getId(), recebida.getId());
        assertThat(service.listarPorStatus(StatusOS.CANCELADA)).hasSize(1);
    }

    // ------------------------------------------------------------ itens

    @Test
    void adicionaServicoEPecaDuranteODiagnostico() {
        OrdemServico os = abrirSimples();
        service.iniciarDiagnostico(os.getId());

        service.adicionarServico(os.getId(), new ItemSolicitado(servico.id(), 2));
        OrdemServico atualizada = service.adicionarPeca(os.getId(), new ItemSolicitado(peca.id(), 1));

        assertThat(atualizada.valorTotal()).isEqualByComparingTo("335.00");
    }

    // ------------------------------------------------------------ orçamento / saga

    @Test
    void solicitarOrcamentoEnviaComandoSemMudarStatus() {
        OrdemServico os = abrirComItens();
        service.iniciarDiagnostico(os.getId());

        OrdemServico resultado = service.solicitarOrcamento(os.getId());

        assertThat(resultado.getStatus()).isEqualTo(StatusOS.EM_DIAGNOSTICO);
        assertThat(saga.comandos).containsExactly("CRIAR_ORCAMENTO:" + os.getId() + ":" + cliente.id());
    }

    @Test
    void solicitarOrcamentoSemDiagnosticoOuSemItensNaoEnviaComando() {
        OrdemServico os = abrirComItens();
        assertThatThrownBy(() -> service.solicitarOrcamento(os.getId()))
                .isInstanceOf(RegraDeNegocioException.class);

        OrdemServico vazia = abrirSimples();
        service.iniciarDiagnostico(vazia.getId());
        assertThatThrownBy(() -> service.solicitarOrcamento(vazia.getId()))
                .isInstanceOf(RegraDeNegocioException.class);

        assertThat(saga.comandos).isEmpty();
    }

    @Test
    void aprovarOrcamentoEnviaCobranca() {
        OrdemServico os = abrirComItens();
        service.iniciarDiagnostico(os.getId());
        service.solicitarOrcamento(os.getId());
        orcamentoCriado(os.getId(), "orc-1");

        service.aprovarOrcamento(os.getId());

        assertThat(saga.comandos).contains("COBRAR_ORCAMENTO:" + os.getId());
    }

    @Test
    void aprovarOuRecusarForaDeAguardandoAprovacaoFalha() {
        OrdemServico os = abrirSimples();
        assertThatThrownBy(() -> service.aprovarOrcamento(os.getId()))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Aguardando Aprovação");
        assertThatThrownBy(() -> service.recusarOrcamento(os.getId(), "caro"))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThat(saga.comandos).isEmpty();
    }

    @Test
    void recusarOrcamentoVoltaParaDiagnosticoECancelaNoBilling() {
        OrdemServico os = abrirComItens();
        service.iniciarDiagnostico(os.getId());
        service.solicitarOrcamento(os.getId());
        orcamentoCriado(os.getId(), "orc-1");

        OrdemServico resultado = service.recusarOrcamento(os.getId(), "Achei caro");

        assertThat(resultado.getStatus()).isEqualTo(StatusOS.EM_DIAGNOSTICO);
        assertThat(resultado.getOrcamentoId()).isNull();
        assertThat(saga.comandos).contains("CANCELAR_ORCAMENTO:" + os.getId() + ":orc-1:Achei caro");
    }

    @Test
    void recusarSemMotivoUsaMotivoPadrao() {
        OrdemServico os = abrirComItens();
        service.iniciarDiagnostico(os.getId());
        service.solicitarOrcamento(os.getId());
        orcamentoCriado(os.getId(), "orc-9");

        service.recusarOrcamento(os.getId(), "  ");

        assertThat(saga.comandos).anyMatch(c -> c.endsWith(":orc-9:Orçamento recusado pelo cliente"));
    }

    // ------------------------------------------------------------ cancelar / entregar

    @Test
    void cancelamentoManualSoEmRecebidaOuEmDiagnostico() {
        OrdemServico a = abrirSimples();
        assertThat(service.cancelar(a.getId(), "desistiu").getStatus()).isEqualTo(StatusOS.CANCELADA);

        OrdemServico b = abrirComItens();
        service.iniciarDiagnostico(b.getId());
        service.solicitarOrcamento(b.getId());
        orcamentoCriado(b.getId(), "orc-2");
        assertThatThrownBy(() -> service.cancelar(b.getId(), "x"))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Cancelamento manual");
    }

    @Test
    void entregaSoDepoisDeFinalizada() {
        OrdemServico os = abrirSimples();
        assertThatThrownBy(() -> service.entregar(os.getId())).isInstanceOf(RegraDeNegocioException.class);

        OrdemServico guardada = ordensRepo.buscarPorId(os.getId()).orElseThrow();
        guardada.iniciarDiagnostico(Instant.now(relogio));
        guardada.aguardarAprovacao("orc-3", Instant.now(relogio));
        guardada.iniciarExecucao(Instant.now(relogio));
        guardada.finalizar(Instant.now(relogio));

        assertThat(service.entregar(os.getId()).getStatus()).isEqualTo(StatusOS.ENTREGUE);
    }
}
