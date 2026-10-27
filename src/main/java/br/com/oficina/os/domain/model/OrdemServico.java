package br.com.oficina.os.domain.model;

import br.com.oficina.os.domain.exception.RegraDeNegocioException;
import br.com.oficina.os.domain.exception.TransicaoStatusInvalidaException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Agregado raiz da Ordem de Serviço. Toda mudança de status passa por aqui, então as regras do
 * fluxo (docs/eventos.md) ficam em um lugar só. O domínio não conhece Saga nem mensageria: quem
 * decide quando chamar cada método é a camada de aplicação.
 *
 * Os métodos recebem o instante atual como parâmetro para os testes serem determinísticos.
 */
public class OrdemServico {

    private Long id;
    private final String numero;
    private final Long clienteId;
    private final Long veiculoId;
    private StatusOS status;
    private final String descricaoProblema;
    private String observacoes;
    private String orcamentoId;
    private String motivoCancelamento;

    private final List<ItemServicoOS> itensServico = new ArrayList<>();
    private final List<ItemPecaOS> itensPeca = new ArrayList<>();
    private final List<HistoricoStatus> historico = new ArrayList<>();

    private final Instant dataAbertura;
    private Instant dataInicio;
    private Instant dataFinalizacao;
    private Instant dataEntrega;

    private OrdemServico(Long id, String numero, Long clienteId, Long veiculoId, StatusOS status,
                         String descricaoProblema, String observacoes, Instant dataAbertura) {
        this.id = id;
        this.numero = numero;
        this.clienteId = clienteId;
        this.veiculoId = veiculoId;
        this.status = status;
        this.descricaoProblema = descricaoProblema;
        this.observacoes = observacoes;
        this.dataAbertura = dataAbertura;
    }

    /** Abre uma OS nova no status RECEBIDA. */
    public static OrdemServico abrir(String numero, Long clienteId, Long veiculoId,
                                     String descricaoProblema, String observacoes, Instant agora) {
        if (numero == null || numero.isBlank()) {
            throw new RegraDeNegocioException("Número da OS é obrigatório");
        }
        if (clienteId == null || veiculoId == null) {
            throw new RegraDeNegocioException("Cliente e veículo são obrigatórios");
        }
        if (descricaoProblema == null || descricaoProblema.isBlank()) {
            throw new RegraDeNegocioException("Descrição do problema é obrigatória");
        }
        OrdemServico os = new OrdemServico(null, numero, clienteId, veiculoId, StatusOS.RECEBIDA,
                descricaoProblema, observacoes, agora);
        os.historico.add(new HistoricoStatus(null, StatusOS.RECEBIDA, agora, "OS aberta"));
        return os;
    }

    /** Dados completos de uma OS já existente, usados pela persistência para reconstruir o agregado. */
    public record Dados(Long id, String numero, Long clienteId, Long veiculoId, StatusOS status,
                        String descricaoProblema, String observacoes, String orcamentoId,
                        String motivoCancelamento, List<ItemServicoOS> itensServico,
                        List<ItemPecaOS> itensPeca, List<HistoricoStatus> historico,
                        Instant dataAbertura, Instant dataInicio, Instant dataFinalizacao,
                        Instant dataEntrega) {
    }

    public static OrdemServico reconstituir(Dados d) {
        OrdemServico os = new OrdemServico(d.id(), d.numero(), d.clienteId(), d.veiculoId(),
                d.status(), d.descricaoProblema(), d.observacoes(), d.dataAbertura());
        os.orcamentoId = d.orcamentoId();
        os.motivoCancelamento = d.motivoCancelamento();
        os.itensServico.addAll(d.itensServico());
        os.itensPeca.addAll(d.itensPeca());
        os.historico.addAll(d.historico());
        os.dataInicio = d.dataInicio();
        os.dataFinalizacao = d.dataFinalizacao();
        os.dataEntrega = d.dataEntrega();
        return os;
    }

    // ---------------------------------------------------------------- itens

    public void adicionarServico(Servico servico, int quantidade) {
        validarEdicaoDeItens();
        validarQuantidade(quantidade);
        if (!servico.ativo()) {
            throw new RegraDeNegocioException("Serviço inativo: " + servico.nome());
        }
        itensServico.add(new ItemServicoOS(servico.id(), servico.nome(), quantidade, servico.preco()));
    }

    public void adicionarPeca(Peca peca, int quantidade) {
        validarEdicaoDeItens();
        validarQuantidade(quantidade);
        if (!peca.ativo()) {
            throw new RegraDeNegocioException("Peça inativa: " + peca.nome());
        }
        itensPeca.add(new ItemPecaOS(peca.id(), peca.codigo(), peca.nome(), quantidade, peca.precoUnitario()));
    }

    private void validarEdicaoDeItens() {
        if (status != StatusOS.RECEBIDA && status != StatusOS.EM_DIAGNOSTICO) {
            throw new RegraDeNegocioException(
                    "Itens só podem ser alterados em OS Recebida ou Em Diagnóstico. Status atual: "
                            + status.getDescricao());
        }
    }

    private static void validarQuantidade(int quantidade) {
        if (quantidade <= 0) {
            throw new RegraDeNegocioException("Quantidade deve ser maior que zero");
        }
    }

    public BigDecimal valorTotal() {
        BigDecimal servicos = itensServico.stream().map(ItemServicoOS::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pecas = itensPeca.stream().map(ItemPecaOS::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return servicos.add(pecas);
    }

    // ---------------------------------------------------------------- fluxo

    public void iniciarDiagnostico(Instant agora) {
        mover(StatusOS.EM_DIAGNOSTICO, "Diagnóstico iniciado", agora);
    }

    /**
     * Confere se a OS pode pedir orçamento (EM_DIAGNOSTICO e com ao menos um item). Não muda o
     * status: ele só avança quando o Billing responde com ORCAMENTO_CRIADO.
     */
    public void validarParaOrcamento() {
        if (status != StatusOS.EM_DIAGNOSTICO) {
            throw new RegraDeNegocioException(
                    "Orçamento só pode ser gerado com a OS Em Diagnóstico. Status atual: " + status.getDescricao());
        }
        if (itensServico.isEmpty() && itensPeca.isEmpty()) {
            throw new RegraDeNegocioException("Adicione ao menos um serviço ou peça antes de gerar o orçamento");
        }
    }

    /** Resposta do Billing: orçamento criado. */
    public void aguardarAprovacao(String idOrcamento, Instant agora) {
        mover(StatusOS.AGUARDANDO_APROVACAO, "Orçamento " + idOrcamento + " criado", agora);
        this.orcamentoId = idOrcamento;
    }

    /** Cliente recusou o orçamento ou o pagamento foi recusado: volta ao diagnóstico. */
    public void voltarParaDiagnostico(String motivo, Instant agora) {
        mover(StatusOS.EM_DIAGNOSTICO, motivo, agora);
        this.orcamentoId = null;
    }

    /** Pagamento aprovado e estoque reservado: serviço começa. */
    public void iniciarExecucao(Instant agora) {
        mover(StatusOS.EM_EXECUCAO, "Pagamento aprovado e estoque reservado", agora);
        this.dataInicio = agora;
    }

    public void finalizar(Instant agora) {
        mover(StatusOS.FINALIZADA, "Execução finalizada", agora);
        this.dataFinalizacao = agora;
    }

    public void entregar(Instant agora) {
        mover(StatusOS.ENTREGUE, "Veículo entregue", agora);
        this.dataEntrega = agora;
    }

    public void cancelar(String motivo, Instant agora) {
        if (motivo == null || motivo.isBlank()) {
            throw new RegraDeNegocioException("Motivo do cancelamento é obrigatório");
        }
        mover(StatusOS.CANCELADA, motivo, agora);
        this.motivoCancelamento = motivo;
    }

    /** Registra no histórico algo que aconteceu sem mudar o status (ex.: execução iniciada, falha de orçamento). */
    public void registrarOcorrencia(String texto, Instant agora) {
        historico.add(new HistoricoStatus(status, status, agora, texto));
    }

    private void mover(StatusOS destino, String motivo, Instant agora) {
        if (!status.podeTransicionarPara(destino)) {
            throw new TransicaoStatusInvalidaException(status, destino);
        }
        historico.add(new HistoricoStatus(status, destino, agora, motivo));
        this.status = destino;
    }

    // ---------------------------------------------------------------- leitura

    public Long getId() { return id; }
    public void atribuirId(Long novoId) { this.id = novoId; }
    public String getNumero() { return numero; }
    public Long getClienteId() { return clienteId; }
    public Long getVeiculoId() { return veiculoId; }
    public StatusOS getStatus() { return status; }
    public String getDescricaoProblema() { return descricaoProblema; }
    public String getObservacoes() { return observacoes; }
    public String getOrcamentoId() { return orcamentoId; }
    public String getMotivoCancelamento() { return motivoCancelamento; }
    public Instant getDataAbertura() { return dataAbertura; }
    public Instant getDataInicio() { return dataInicio; }
    public Instant getDataFinalizacao() { return dataFinalizacao; }
    public Instant getDataEntrega() { return dataEntrega; }

    public List<ItemServicoOS> getItensServico() { return Collections.unmodifiableList(itensServico); }
    public List<ItemPecaOS> getItensPeca() { return Collections.unmodifiableList(itensPeca); }
    public List<HistoricoStatus> getHistorico() { return Collections.unmodifiableList(historico); }
}
