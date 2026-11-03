package br.com.oficina.os.domain.model;

import br.com.oficina.os.domain.exception.RegraDeNegocioException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Instância da Saga de uma OS: guarda em que passo ela está, os ids devolvidos pelos outros serviços
 * (orçamento, pagamento, reserva) e o diário do que aconteceu. Não conhece mensageria nem banco.
 */
public class Saga {

    private final String id;
    private final Long osId;
    private final String correlationId;
    private EstadoSaga estado;
    private String orcamentoId;
    private BigDecimal valorTotal;
    private String metodoPagamento;
    private String pagamentoId;
    private String linkPagamento;
    private String reservaId;
    private String motivo;
    private int tentativas;
    private Instant prazo;
    private final Instant criadaEm;
    private Instant atualizadaEm;
    private long versao;
    private final List<PassoSaga> passos = new ArrayList<>();

    private Saga(String id, Long osId, String correlationId, EstadoSaga estado, Instant criadaEm) {
        this.id = id;
        this.osId = osId;
        this.correlationId = correlationId;
        this.estado = estado;
        this.criadaEm = criadaEm;
        this.atualizadaEm = criadaEm;
    }

    /** Saga nova: o pedido de orçamento está sendo enviado ao Billing. */
    public static Saga iniciar(String id, Long osId, String correlationId, Instant agora, Instant prazo) {
        if (id == null || id.isBlank() || osId == null) {
            throw new RegraDeNegocioException("Saga precisa de id e de OS");
        }
        Saga s = new Saga(id, osId, correlationId, EstadoSaga.ORCAMENTO_SOLICITADO, agora);
        s.prazo = prazo;
        s.passos.add(new PassoSaga(agora, "SAGA_INICIADA", "OS " + osId));
        return s;
    }

    /** Dados completos de uma Saga existente, usados pela persistência para reconstruir o objeto. */
    public record Dados(String id, Long osId, String correlationId, EstadoSaga estado, String orcamentoId,
                        BigDecimal valorTotal, String metodoPagamento, String pagamentoId, String linkPagamento,
                        String reservaId,
                        String motivo, int tentativas, Instant prazo, Instant criadaEm, Instant atualizadaEm,
                        long versao, List<PassoSaga> passos) {
    }

    public static Saga reconstituir(Dados d) {
        Saga s = new Saga(d.id(), d.osId(), d.correlationId(), d.estado(), d.criadaEm());
        s.orcamentoId = d.orcamentoId();
        s.valorTotal = d.valorTotal();
        s.metodoPagamento = d.metodoPagamento();
        s.pagamentoId = d.pagamentoId();
        s.linkPagamento = d.linkPagamento();
        s.reservaId = d.reservaId();
        s.motivo = d.motivo();
        s.tentativas = d.tentativas();
        s.prazo = d.prazo();
        s.atualizadaEm = d.atualizadaEm();
        s.versao = d.versao();
        s.passos.addAll(d.passos());
        return s;
    }

    // ---------------------------------------------------------------- transições

    /**
     * Muda de passo. {@code prazo} só vale para estados que aguardam resposta; os demais ficam sem prazo.
     * Uma Saga terminada não volta a andar (exceto as pendências de ação manual).
     */
    public void mover(EstadoSaga novo, String detalhe, Instant agora, Instant novoPrazo) {
        if (estado.isTerminal() && !estado.pendenteDeAcaoManual()) {
            throw new RegraDeNegocioException("A Saga " + id + " já terminou (" + estado + ")");
        }
        this.passos.add(new PassoSaga(agora, novo.name(), detalhe));
        this.estado = novo;
        this.tentativas = 0;
        this.prazo = novo.aguardaResposta() ? novoPrazo : null;
        this.atualizadaEm = agora;
    }

    /** O comando do passo atual foi enviado de novo (a resposta não veio a tempo). */
    public void reenviar(Instant agora, Instant novoPrazo) {
        if (!estado.aguardaResposta()) {
            throw new RegraDeNegocioException("A Saga " + id + " não está aguardando resposta (" + estado + ")");
        }
        this.tentativas++;
        this.prazo = novoPrazo;
        this.atualizadaEm = agora;
        this.passos.add(new PassoSaga(agora, "REENVIO", estado.name() + " (tentativa " + (tentativas + 1) + ")"));
    }

    /** Anota algo no diário sem mudar de passo (ex.: evento informativo). */
    public void anotar(String tipo, String detalhe, Instant agora) {
        this.passos.add(new PassoSaga(agora, tipo, detalhe));
        this.atualizadaEm = agora;
    }

    public void registrarOrcamento(String idOrcamento, BigDecimal total) {
        this.orcamentoId = idOrcamento;
        this.valorTotal = total;
    }

    public void registrarMetodoPagamento(String metodo) {
        this.metodoPagamento = metodo;
    }

    public void registrarPagamento(String idPagamento) {
        this.pagamentoId = idPagamento;
    }

    public void registrarLink(String link) {
        this.linkPagamento = link;
    }

    public void registrarReserva(String idReserva) {
        this.reservaId = idReserva;
    }

    public void registrarMotivo(String texto) {
        this.motivo = texto;
    }

    // ---------------------------------------------------------------- leitura

    public String getId() { return id; }
    public Long getOsId() { return osId; }
    public String getCorrelationId() { return correlationId; }
    public EstadoSaga getEstado() { return estado; }
    public String getOrcamentoId() { return orcamentoId; }
    public BigDecimal getValorTotal() { return valorTotal; }
    public String getMetodoPagamento() { return metodoPagamento; }
    public String getPagamentoId() { return pagamentoId; }
    public String getLinkPagamento() { return linkPagamento; }
    public String getReservaId() { return reservaId; }
    public String getMotivo() { return motivo; }
    public int getTentativas() { return tentativas; }
    public Instant getPrazo() { return prazo; }
    public Instant getCriadaEm() { return criadaEm; }
    public Instant getAtualizadaEm() { return atualizadaEm; }
    public long getVersao() { return versao; }
    public List<PassoSaga> getPassos() { return Collections.unmodifiableList(passos); }
}
