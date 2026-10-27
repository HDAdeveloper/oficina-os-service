package br.com.oficina.os.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Status da Ordem de Serviço. As transições permitidas estão documentadas em docs/eventos.md.
 * CANCELADA é o resultado das compensações da Saga (ou de um cancelamento manual antes do pagamento).
 */
public enum StatusOS {
    RECEBIDA("Recebida"),
    EM_DIAGNOSTICO("Em Diagnóstico"),
    AGUARDANDO_APROVACAO("Aguardando Aprovação"),
    EM_EXECUCAO("Em Execução"),
    FINALIZADA("Finalizada"),
    ENTREGUE("Entregue"),
    CANCELADA("Cancelada");

    private final String descricao;

    StatusOS(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public Set<StatusOS> destinosPermitidos() {
        return switch (this) {
            case RECEBIDA -> EnumSet.of(EM_DIAGNOSTICO, CANCELADA);
            case EM_DIAGNOSTICO -> EnumSet.of(AGUARDANDO_APROVACAO, CANCELADA);
            case AGUARDANDO_APROVACAO -> EnumSet.of(EM_EXECUCAO, EM_DIAGNOSTICO, CANCELADA);
            case EM_EXECUCAO -> EnumSet.of(FINALIZADA, CANCELADA);
            case FINALIZADA -> EnumSet.of(ENTREGUE);
            case ENTREGUE, CANCELADA -> EnumSet.noneOf(StatusOS.class);
        };
    }

    public boolean podeTransicionarPara(StatusOS destino) {
        return destinosPermitidos().contains(destino);
    }

    /** OS ainda em andamento (não finalizada, entregue nem cancelada). */
    public boolean isAtiva() {
        return this == RECEBIDA || this == EM_DIAGNOSTICO
                || this == AGUARDANDO_APROVACAO || this == EM_EXECUCAO;
    }

    /** Ordem de exibição da listagem de OS ativas: quanto menor, mais prioritária. */
    public int prioridade() {
        return switch (this) {
            case EM_EXECUCAO -> 0;
            case AGUARDANDO_APROVACAO -> 1;
            case EM_DIAGNOSTICO -> 2;
            case RECEBIDA -> 3;
            default -> 99;
        };
    }
}
