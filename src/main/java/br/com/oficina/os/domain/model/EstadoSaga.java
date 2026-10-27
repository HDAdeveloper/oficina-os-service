package br.com.oficina.os.domain.model;

/**
 * Passo em que a Saga de uma OS se encontra. Os sub-passos do pagamento e da reserva vivem aqui e não no
 * status da OS (docs/eventos.md). "Aguarda resposta" indica que um comando foi enviado e a resposta tem prazo.
 */
public enum EstadoSaga {
    ORCAMENTO_SOLICITADO(true, false),
    AGUARDANDO_APROVACAO(false, false),
    COBRANDO(true, false),
    RESERVANDO_ESTOQUE(true, false),
    EM_EXECUCAO(false, false),
    /** Compensação A: o orçamento está sendo cancelado no Billing. */
    CANCELANDO_ORCAMENTO(true, false),
    /** Compensação C, passo 1: o estoque está sendo devolvido. */
    LIBERANDO_ESTOQUE(true, false),
    /** Compensações B e C, último passo: o pagamento está sendo estornado. */
    ESTORNANDO(true, false),

    CONCLUIDA(false, true),
    /** Compensação B ou C concluída: OS cancelada, pagamento estornado. */
    CANCELADA(false, true),
    ORCAMENTO_FALHOU(false, true),
    /** Fim sem efeito a desfazer (orçamento recusado e cancelado, cancelamento manual...). */
    ENCERRADA(false, true),
    /** Estorno não confirmado dentro das tentativas: exige ação manual (alerta). */
    ESTORNO_PENDENTE(false, true),
    /** Devolução de estoque não confirmada dentro das tentativas: exige ação manual (alerta). */
    LIBERACAO_PENDENTE(false, true);

    private final boolean aguardaResposta;
    private final boolean terminal;

    EstadoSaga(boolean aguardaResposta, boolean terminal) {
        this.aguardaResposta = aguardaResposta;
        this.terminal = terminal;
    }

    public boolean aguardaResposta() {
        return aguardaResposta;
    }

    public boolean isTerminal() {
        return terminal;
    }

    /** Pendências que a operação resolve reprocessando a mensagem da DLQ: a Saga pode voltar a andar. */
    public boolean pendenteDeAcaoManual() {
        return this == ESTORNO_PENDENTE || this == LIBERACAO_PENDENTE;
    }
}
