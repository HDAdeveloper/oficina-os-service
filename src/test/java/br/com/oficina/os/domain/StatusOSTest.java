package br.com.oficina.os.domain;

import br.com.oficina.os.domain.model.StatusOS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class StatusOSTest {

    @ParameterizedTest(name = "{0} -> {1} é permitido")
    @CsvSource({
            "RECEBIDA, EM_DIAGNOSTICO",
            "RECEBIDA, CANCELADA",
            "EM_DIAGNOSTICO, AGUARDANDO_APROVACAO",
            "EM_DIAGNOSTICO, CANCELADA",
            "AGUARDANDO_APROVACAO, EM_EXECUCAO",
            "AGUARDANDO_APROVACAO, EM_DIAGNOSTICO",
            "AGUARDANDO_APROVACAO, CANCELADA",
            "EM_EXECUCAO, FINALIZADA",
            "EM_EXECUCAO, CANCELADA",
            "FINALIZADA, ENTREGUE"
    })
    void transicoesPermitidas(StatusOS de, StatusOS para) {
        assertThat(de.podeTransicionarPara(para)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} é proibido")
    @CsvSource({
            "RECEBIDA, EM_EXECUCAO",
            "RECEBIDA, FINALIZADA",
            "EM_DIAGNOSTICO, EM_EXECUCAO",
            "AGUARDANDO_APROVACAO, FINALIZADA",
            "EM_EXECUCAO, EM_DIAGNOSTICO",
            "EM_EXECUCAO, ENTREGUE",
            "FINALIZADA, CANCELADA",
            "FINALIZADA, EM_EXECUCAO",
            "ENTREGUE, CANCELADA",
            "CANCELADA, EM_DIAGNOSTICO"
    })
    void transicoesProibidas(StatusOS de, StatusOS para) {
        assertThat(de.podeTransicionarPara(para)).isFalse();
    }

    @Test
    void statusTerminaisNaoTemDestino() {
        assertThat(StatusOS.ENTREGUE.destinosPermitidos()).isEmpty();
        assertThat(StatusOS.CANCELADA.destinosPermitidos()).isEmpty();
    }

    @Test
    void apenasStatusEmAndamentoSaoAtivos() {
        assertThat(StatusOS.RECEBIDA.isAtiva()).isTrue();
        assertThat(StatusOS.EM_DIAGNOSTICO.isAtiva()).isTrue();
        assertThat(StatusOS.AGUARDANDO_APROVACAO.isAtiva()).isTrue();
        assertThat(StatusOS.EM_EXECUCAO.isAtiva()).isTrue();
        assertThat(StatusOS.FINALIZADA.isAtiva()).isFalse();
        assertThat(StatusOS.ENTREGUE.isAtiva()).isFalse();
        assertThat(StatusOS.CANCELADA.isAtiva()).isFalse();
    }

    @Test
    void prioridadeSegueAOrdemDaListagem() {
        assertThat(StatusOS.EM_EXECUCAO.prioridade())
                .isLessThan(StatusOS.AGUARDANDO_APROVACAO.prioridade());
        assertThat(StatusOS.AGUARDANDO_APROVACAO.prioridade())
                .isLessThan(StatusOS.EM_DIAGNOSTICO.prioridade());
        assertThat(StatusOS.EM_DIAGNOSTICO.prioridade())
                .isLessThan(StatusOS.RECEBIDA.prioridade());
        assertThat(StatusOS.ENTREGUE.prioridade()).isEqualTo(99);
    }

    @Test
    void descricaoLegivel() {
        assertThat(StatusOS.AGUARDANDO_APROVACAO.getDescricao()).isEqualTo("Aguardando Aprovação");
    }
}
