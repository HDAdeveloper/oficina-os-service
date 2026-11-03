package br.com.oficina.os.application.service;

import br.com.oficina.os.application.port.in.ContextoMensagem;
import br.com.oficina.os.application.port.in.EventoSaga;
import br.com.oficina.os.application.port.in.EventoSaga.EstoqueInsuficiente;
import br.com.oficina.os.application.port.in.EventoSaga.EstoqueLiberado;
import br.com.oficina.os.application.port.in.EventoSaga.EstoqueReservado;
import br.com.oficina.os.application.port.in.EventoSaga.ExecucaoFalhou;
import br.com.oficina.os.application.port.in.EventoSaga.ExecucaoFinalizada;
import br.com.oficina.os.application.port.in.EventoSaga.ExecucaoIniciada;
import br.com.oficina.os.application.port.in.EventoSaga.OrcamentoCancelado;
import br.com.oficina.os.application.port.in.EventoSaga.OrcamentoCriado;
import br.com.oficina.os.application.port.in.EventoSaga.LinkPagamentoGerado;
import br.com.oficina.os.application.port.in.EventoSaga.OrcamentoFalhou;
import br.com.oficina.os.application.port.in.EventoSaga.PagamentoAprovado;
import br.com.oficina.os.application.port.in.EventoSaga.PagamentoEstornado;
import br.com.oficina.os.application.port.in.EventoSaga.PagamentoRecusado;
import br.com.oficina.os.application.port.in.ProcessadorEventos;
import br.com.oficina.os.application.port.out.ClienteRepository;
import br.com.oficina.os.application.port.out.ContextoRequisicao;
import br.com.oficina.os.application.port.out.GeradorId;
import br.com.oficina.os.application.port.out.MensagemProcessadaRepository;
import br.com.oficina.os.application.port.out.OrdemServicoRepository;
import br.com.oficina.os.application.port.out.SagaCommandPort;
import br.com.oficina.os.application.port.out.SagaRepository;
import br.com.oficina.os.domain.exception.RegraDeNegocioException;
import br.com.oficina.os.domain.model.Cliente;
import br.com.oficina.os.domain.model.EstadoSaga;
import br.com.oficina.os.domain.model.OrdemServico;
import br.com.oficina.os.domain.model.Saga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Orquestrador da Saga. Cada resposta dos outros serviços decide o próximo comando; quando um passo falha,
 * as compensações rodam em ordem inversa (docs/eventos.md):
 * <ul>
 *   <li>A) pagamento recusado ou link de pagamento vencido: cancela o orçamento, OS volta ao diagnóstico;</li>
 *   <li>B) estoque insuficiente: estorna o pagamento, OS cancelada;</li>
 *   <li>C) falha na execução: libera o estoque, depois estorna o pagamento, OS cancelada.</li>
 * </ul>
 * Cada chamada roda numa transação única (estado da OS + estado da Saga + comando na outbox).
 */
public class OrquestradorSaga implements ProcessadorEventos {

    private static final Logger LOG = LoggerFactory.getLogger(OrquestradorSaga.class);
    /** Validade assumida quando o Billing não informa até quando o link vale. */
    private static final Duration VALIDADE_PADRAO_DO_LINK = Duration.ofHours(24);

    private final SagaRepository sagas;
    private final MensagemProcessadaRepository processadas;
    private final OrdemServicoRepository ordens;
    private final ClienteRepository clientes;
    private final SagaCommandPort comandos;
    private final GeradorId ids;
    private final ContextoRequisicao requisicao;
    private final Clock clock;
    private final Duration prazoResposta;
    private final int maxReenvios;

    public OrquestradorSaga(SagaRepository sagas, MensagemProcessadaRepository processadas,
                            OrdemServicoRepository ordens, ClienteRepository clientes, SagaCommandPort comandos,
                            GeradorId ids, ContextoRequisicao requisicao, Clock clock, Duration prazoResposta,
                            int maxReenvios) {
        this.sagas = sagas;
        this.processadas = processadas;
        this.ordens = ordens;
        this.clientes = clientes;
        this.comandos = comandos;
        this.ids = ids;
        this.requisicao = requisicao;
        this.clock = clock;
        this.prazoResposta = prazoResposta;
        this.maxReenvios = maxReenvios;
    }

    // ================================================================ ações do atendente / cliente (REST)

    /** Inicia a Saga (ou reenvia o pedido, se já há uma aguardando o orçamento) e envia CRIAR_ORCAMENTO. */
    public Saga solicitarOrcamento(OrdemServico os, Cliente cliente) {
        Instant agora = agora();
        Saga saga = sagas.buscarPorOs(os.getId()).stream()
                .filter(s -> s.getEstado() == EstadoSaga.ORCAMENTO_SOLICITADO).findFirst().orElse(null);
        if (saga == null) {
            saga = Saga.iniciar(ids.novo(), os.getId(), requisicao.correlationId(), agora, prazo(agora));
        } else {
            saga.reenviar(agora, prazo(agora));
        }
        saga = sagas.salvar(saga);
        comandos.criarOrcamento(saga, os, cliente);
        return saga;
    }

    /** Cliente aprovou o orçamento: envia a cobrança. Exige uma Saga aguardando aprovação. */
    public Saga cobrar(OrdemServico os, String metodoPagamento) {
        Saga saga = exigirSaga(os, EstadoSaga.AGUARDANDO_APROVACAO, "aprovado");
        Instant agora = agora();
        saga.registrarMetodoPagamento(metodoPagamento);
        saga.mover(EstadoSaga.COBRANDO, "Cobrança do orçamento " + saga.getOrcamentoId(), agora, prazo(agora));
        saga = sagas.salvar(saga);
        comandos.cobrarOrcamento(saga, os);
        return saga;
    }

    /** Cliente recusou o orçamento (a OS já voltou ao diagnóstico): cancela o orçamento no Billing. */
    public Saga recusar(OrdemServico os, String orcamentoId, String motivo) {
        Saga saga = exigirSaga(os, EstadoSaga.AGUARDANDO_APROVACAO, "recusado");
        Instant agora = agora();
        saga.registrarMotivo(motivo);
        saga.mover(EstadoSaga.CANCELANDO_ORCAMENTO, motivo, agora, prazo(agora));
        saga = sagas.salvar(saga);
        comandos.cancelarOrcamento(saga, os, orcamentoId, motivo);
        return saga;
    }

    /** Verifica se a OS pode receber a decisão do cliente (há Saga aguardando aprovação). */
    public void exigirAguardandoAprovacao(OrdemServico os, String acao) {
        exigirSaga(os, EstadoSaga.AGUARDANDO_APROVACAO, acao);
    }

    /** Cancelamento manual: encerra a Saga que ainda esperava o orçamento (respostas tardias serão ignoradas). */
    public void cancelamentoManual(Long osId, String motivo) {
        Instant agora = agora();
        for (Saga s : sagas.buscarPorOs(osId)) {
            if (s.getEstado() == EstadoSaga.ORCAMENTO_SOLICITADO) {
                s.mover(EstadoSaga.ENCERRADA, "OS cancelada manualmente: " + motivo, agora, null);
                sagas.salvar(s);
            }
        }
    }

    public Optional<Saga> sagaAtual(Long osId) {
        return sagas.buscarPorOs(osId).stream().findFirst();
    }

    public List<Saga> sagasDaOs(Long osId) {
        return sagas.buscarPorOs(osId);
    }

    /** As Sagas mexidas mais recentemente, para o painel de acompanhamento (limite entre 1 e 100). */
    public List<Saga> sagasRecentes(int limite) {
        return sagas.buscarRecentes(Math.min(Math.max(limite, 1), 100));
    }

    private Saga exigirSaga(OrdemServico os, EstadoSaga estado, String acao) {
        return sagas.buscarPorOs(os.getId()).stream().filter(s -> s.getEstado() == estado).findFirst()
                .orElseThrow(() -> new RegraDeNegocioException("O orçamento não pode ser " + acao
                        + ": não há Saga aguardando a decisão do cliente para a OS " + os.getId()));
    }

    // ================================================================ eventos (mensageria)

    @Override
    public void processar(ContextoMensagem ctx, EventoSaga evento) {
        Instant agora = agora();
        if (!processadas.registrar(ctx.messageId(), agora)) {
            LOG.info("Mensagem {} já processada; ignorada", ctx.messageId());
            return;
        }
        Saga saga = sagas.buscarPorId(ctx.sagaId()).orElse(null);
        if (saga == null || (ctx.osId() != null && !ctx.osId().equals(saga.getOsId()))) {
            LOG.warn("Evento {} para Saga desconhecida (sagaId={}, osId={}); ignorado",
                    evento.getClass().getSimpleName(), ctx.sagaId(), ctx.osId());
            return;
        }
        OrdemServico os = ordens.buscarPorId(saga.getOsId()).orElse(null);
        if (os == null) {
            LOG.warn("OS {} da Saga {} não existe; evento ignorado", saga.getOsId(), saga.getId());
            return;
        }
        String nome = evento.getClass().getSimpleName();
        if (!aceita(saga.getEstado(), evento)) {
            saga.anotar("EVENTO_IGNORADO", nome + " em " + saga.getEstado(), agora);
            sagas.salvar(saga);
            LOG.info("Evento {} não cabe no passo {} da Saga {}; ignorado", nome, saga.getEstado(), saga.getId());
            return;
        }
        saga.anotar("EVENTO", nome, agora);
        switch (evento) {
            case OrcamentoCriado e -> orcamentoCriado(saga, os, e, agora);
            case OrcamentoFalhou e -> orcamentoFalhou(saga, os, e, agora);
            case OrcamentoCancelado e -> encerrar(saga, "Orçamento cancelado", agora);
            case LinkPagamentoGerado e -> linkGerado(saga, e, agora);
            case PagamentoAprovado e -> pagamentoAprovado(saga, os, e, agora);
            case PagamentoRecusado e -> compensarA(saga, os, e.motivo(), agora);
            case EstoqueReservado e -> estoqueReservado(saga, os, e, agora);
            case EstoqueInsuficiente e -> compensarB(saga, os, "Estoque insuficiente: " + e.descricao(), agora);
            case ExecucaoIniciada e -> execucaoIniciada(saga, os, agora);
            case ExecucaoFinalizada e -> execucaoFinalizada(saga, os, agora);
            case ExecucaoFalhou e -> compensarC(saga, os, "Falha na execução: " + e.motivo(), agora);
            case EstoqueLiberado e -> estoqueLiberado(saga, os, agora);
            case PagamentoEstornado e -> pagamentoEstornado(saga, os, agora);
        }
    }

    /** Qual evento é esperado em qual passo. Os demais são respostas atrasadas ou repetidas. */
    private static boolean aceita(EstadoSaga estado, EventoSaga evento) {
        return switch (evento) {
            case OrcamentoCriado e -> estado == EstadoSaga.ORCAMENTO_SOLICITADO;
            case OrcamentoFalhou e -> estado == EstadoSaga.ORCAMENTO_SOLICITADO;
            case OrcamentoCancelado e -> estado == EstadoSaga.CANCELANDO_ORCAMENTO;
            case LinkPagamentoGerado e -> estado == EstadoSaga.COBRANDO;
            case PagamentoAprovado e -> estado == EstadoSaga.COBRANDO || estado == EstadoSaga.AGUARDANDO_PAGAMENTO;
            case PagamentoRecusado e -> estado == EstadoSaga.COBRANDO || estado == EstadoSaga.AGUARDANDO_PAGAMENTO;
            case EstoqueReservado e -> estado == EstadoSaga.RESERVANDO_ESTOQUE;
            case EstoqueInsuficiente e -> estado == EstadoSaga.RESERVANDO_ESTOQUE;
            case ExecucaoIniciada e -> estado == EstadoSaga.EM_EXECUCAO;
            case ExecucaoFinalizada e -> estado == EstadoSaga.EM_EXECUCAO;
            case ExecucaoFalhou e -> estado == EstadoSaga.EM_EXECUCAO;
            case EstoqueLiberado e -> estado == EstadoSaga.LIBERANDO_ESTOQUE
                    || estado == EstadoSaga.LIBERACAO_PENDENTE;
            case PagamentoEstornado e -> estado == EstadoSaga.ESTORNANDO || estado == EstadoSaga.ESTORNO_PENDENTE;
        };
    }

    // ---------------------------------------------------------------- fluxo feliz

    private void orcamentoCriado(Saga saga, OrdemServico os, OrcamentoCriado e, Instant agora) {
        os.aguardarAprovacao(e.orcamentoId(), agora);
        ordens.salvar(os);
        saga.registrarOrcamento(e.orcamentoId(), e.valorTotal());
        saga.mover(EstadoSaga.AGUARDANDO_APROVACAO, "Orçamento " + e.orcamentoId(), agora, null);
        sagas.salvar(saga);
    }

    private void orcamentoFalhou(Saga saga, OrdemServico os, OrcamentoFalhou e, Instant agora) {
        os.registrarOcorrencia("Orçamento não gerado: " + e.motivo(), agora);
        ordens.salvar(os);
        saga.registrarMotivo(e.motivo());
        saga.mover(EstadoSaga.ORCAMENTO_FALHOU, e.motivo(), agora, null);
        sagas.salvar(saga);
    }

    /**
     * Pagamento por link: a Saga espera o cliente pagar. O prazo passa a ser a validade do link (mais a folga de
     * uma resposta), porque o Billing é quem vence o link e avisa; se ele não avisar, o prazo compensa daqui.
     */
    private void linkGerado(Saga saga, LinkPagamentoGerado e, Instant agora) {
        Instant validade = e.expiraEm() != null ? e.expiraEm() : agora.plus(VALIDADE_PADRAO_DO_LINK);
        saga.registrarPagamento(e.pagamentoId());
        saga.registrarLink(e.link());
        saga.mover(EstadoSaga.AGUARDANDO_PAGAMENTO, "Link de pagamento gerado (válido até " + validade + ")", agora,
                validade.plus(prazoResposta));
        sagas.salvar(saga);
    }

    private void pagamentoAprovado(Saga saga, OrdemServico os, PagamentoAprovado e, Instant agora) {
        saga.registrarPagamento(e.pagamentoId());
        saga.mover(EstadoSaga.RESERVANDO_ESTOQUE, "Pagamento " + e.pagamentoId(), agora, prazo(agora));
        sagas.salvar(saga);
        comandos.reservarEstoque(saga, os);
    }

    private void estoqueReservado(Saga saga, OrdemServico os, EstoqueReservado e, Instant agora) {
        os.iniciarExecucao(agora);
        ordens.salvar(os);
        saga.registrarReserva(e.reservaId());
        saga.mover(EstadoSaga.EM_EXECUCAO, "Reserva " + e.reservaId(), agora, null);
        sagas.salvar(saga);
    }

    private void execucaoIniciada(Saga saga, OrdemServico os, Instant agora) {
        os.registrarOcorrencia("Reparo iniciado pela oficina", agora);
        ordens.salvar(os);
        sagas.salvar(saga);
    }

    private void execucaoFinalizada(Saga saga, OrdemServico os, Instant agora) {
        os.finalizar(agora);
        ordens.salvar(os);
        saga.mover(EstadoSaga.CONCLUIDA, "Execução finalizada", agora, null);
        sagas.salvar(saga);
    }

    private void encerrar(Saga saga, String detalhe, Instant agora) {
        saga.mover(EstadoSaga.ENCERRADA, detalhe, agora, null);
        sagas.salvar(saga);
    }

    // ---------------------------------------------------------------- compensações

    /** A) Pagamento recusado: cancela o orçamento e devolve a OS ao diagnóstico. */
    private void compensarA(Saga saga, OrdemServico os, String motivo, Instant agora) {
        String texto = textoOu(motivo, "Pagamento recusado");
        String orcamentoId = saga.getOrcamentoId();
        os.voltarParaDiagnostico("Pagamento recusado: " + texto, agora);
        ordens.salvar(os);
        saga.registrarMotivo(texto);
        saga.mover(EstadoSaga.CANCELANDO_ORCAMENTO, texto, agora, prazo(agora));
        sagas.salvar(saga);
        comandos.cancelarOrcamento(saga, os, orcamentoId, texto);
    }

    /** B) Sem estoque (pagamento já feito): estorna o pagamento. */
    private void compensarB(Saga saga, OrdemServico os, String motivo, Instant agora) {
        iniciarEstorno(saga, os, motivo, agora);
    }

    /** C) Falha na execução: primeiro devolve o estoque, o estorno vem depois da confirmação. */
    private void compensarC(Saga saga, OrdemServico os, String motivo, Instant agora) {
        saga.registrarMotivo(motivo);
        saga.mover(EstadoSaga.LIBERANDO_ESTOQUE, motivo, agora, prazo(agora));
        sagas.salvar(saga);
        comandos.liberarEstoque(saga, os, motivo);
    }

    private void estoqueLiberado(Saga saga, OrdemServico os, Instant agora) {
        iniciarEstorno(saga, os, saga.getMotivo(), agora);
    }

    private void iniciarEstorno(Saga saga, OrdemServico os, String motivo, Instant agora) {
        saga.registrarMotivo(motivo);
        saga.mover(EstadoSaga.ESTORNANDO, motivo, agora, prazo(agora));
        sagas.salvar(saga);
        comandos.estornarPagamento(saga, os, saga.getPagamentoId(), motivo);
    }

    private void pagamentoEstornado(Saga saga, OrdemServico os, Instant agora) {
        os.cancelar(textoOu(saga.getMotivo(), "Compensação da Saga"), agora);
        ordens.salvar(os);
        saga.mover(EstadoSaga.CANCELADA, "Pagamento estornado; OS cancelada", agora, null);
        sagas.salvar(saga);
    }

    // ================================================================ prazos

    @Override
    public int verificarPrazos() {
        Instant agora = agora();
        List<Saga> vencidas = sagas.buscarComPrazoVencido(agora);
        for (Saga saga : vencidas) {
            OrdemServico os = ordens.buscarPorId(saga.getOsId()).orElse(null);
            if (os == null) {
                continue;
            }
            // Link de pagamento não se reenvia: passou da validade sem pagamento, compensa direto
            if (saga.getEstado() != EstadoSaga.AGUARDANDO_PAGAMENTO && saga.getTentativas() < maxReenvios) {
                reenviar(saga, os, agora);
            } else {
                desistir(saga, os, agora);
            }
        }
        return vencidas.size();
    }

    private void reenviar(Saga saga, OrdemServico os, Instant agora) {
        LOG.warn("Sem resposta no passo {} da Saga {}; reenviando (tentativa {})", saga.getEstado(), saga.getId(),
                saga.getTentativas() + 2);
        saga.reenviar(agora, prazo(agora));
        sagas.salvar(saga);
        switch (saga.getEstado()) {
            case ORCAMENTO_SOLICITADO -> comandos.criarOrcamento(saga, os, clienteDe(os));
            case COBRANDO -> comandos.cobrarOrcamento(saga, os);
            case RESERVANDO_ESTOQUE -> comandos.reservarEstoque(saga, os);
            case CANCELANDO_ORCAMENTO -> comandos.cancelarOrcamento(saga, os, saga.getOrcamentoId(), saga.getMotivo());
            case LIBERANDO_ESTOQUE -> comandos.liberarEstoque(saga, os, saga.getMotivo());
            case ESTORNANDO -> comandos.estornarPagamento(saga, os, saga.getPagamentoId(), saga.getMotivo());
            default -> throw new IllegalStateException("Estado sem reenvio: " + saga.getEstado());
        }
    }

    /** Tentativas esgotadas: compensa ou deixa a Saga pendente para ação manual. */
    private void desistir(Saga saga, OrdemServico os, Instant agora) {
        LOG.error("Tentativas esgotadas no passo {} da Saga {}", saga.getEstado(), saga.getId());
        switch (saga.getEstado()) {
            case ORCAMENTO_SOLICITADO -> {
                os.registrarOcorrencia("Orçamento não gerado: o Billing não respondeu", agora);
                ordens.salvar(os);
                saga.mover(EstadoSaga.ORCAMENTO_FALHOU, "Sem resposta do Billing", agora, null);
                sagas.salvar(saga);
            }
            case COBRANDO -> compensarA(saga, os, "Sem resposta do Billing ao cobrar", agora);
            case AGUARDANDO_PAGAMENTO -> compensarA(saga, os, "Link de pagamento expirou sem pagamento", agora);
            case RESERVANDO_ESTOQUE -> compensarB(saga, os, "Sem resposta do Execution ao reservar estoque", agora);
            case CANCELANDO_ORCAMENTO -> encerrar(saga, "Cancelamento do orçamento sem confirmação", agora);
            case LIBERANDO_ESTOQUE -> pendente(saga, os, EstadoSaga.LIBERACAO_PENDENTE, agora);
            case ESTORNANDO -> pendente(saga, os, EstadoSaga.ESTORNO_PENDENTE, agora);
            default -> throw new IllegalStateException("Estado sem prazo: " + saga.getEstado());
        }
    }

    private void pendente(Saga saga, OrdemServico os, EstadoSaga estado, Instant agora) {
        os.registrarOcorrencia("Compensação pendente (" + estado + "): exige ação manual", agora);
        ordens.salvar(os);
        saga.mover(estado, "Sem confirmação após as tentativas; ação manual necessária", agora, null);
        sagas.salvar(saga);
    }

    // ================================================================ apoio

    private Cliente clienteDe(OrdemServico os) {
        return clientes.buscarPorId(os.getClienteId())
                .orElseThrow(() -> new IllegalStateException("Cliente " + os.getClienteId() + " não existe"));
    }

    private Instant prazo(Instant agora) {
        return agora.plus(prazoResposta);
    }

    private Instant agora() {
        return Instant.now(clock);
    }

    private static String textoOu(String texto, String padrao) {
        return texto == null || texto.isBlank() ? padrao : texto;
    }
}
