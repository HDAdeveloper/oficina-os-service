package br.com.oficina.os.domain;

import br.com.oficina.os.domain.exception.RegraDeNegocioException;
import br.com.oficina.os.domain.exception.TransicaoStatusInvalidaException;
import br.com.oficina.os.domain.model.HistoricoStatus;
import br.com.oficina.os.domain.model.ItemServicoOS;
import br.com.oficina.os.domain.model.OrdemServico;
import br.com.oficina.os.domain.model.Peca;
import br.com.oficina.os.domain.model.Servico;
import br.com.oficina.os.domain.model.StatusOS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrdemServicoTest {

    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");

    private final Servico troca = new Servico(1L, "Troca de óleo", null, new BigDecimal("120.50"), 30, true);
    private final Peca filtro = new Peca(2L, "FLT-01", "Filtro de óleo", null, new BigDecimal("35.00"), true);

    private OrdemServico os;

    @BeforeEach
    void abreOS() {
        os = OrdemServico.abrir("OS-2026-1", "52998224725", 1L, "Barulho no motor", null, T0);
    }

    private OrdemServico osEmExecucao() {
        os.iniciarDiagnostico(T0);
        os.adicionarServico(troca, 1);
        os.aguardarAprovacao("ORC-1", T0);
        os.iniciarExecucao(T0.plusSeconds(60));
        return os;
    }

    // ---------------------------------------------------------------- abertura

    @Test
    void abreNoStatusRecebidaComHistorico() {
        assertThat(os.getStatus()).isEqualTo(StatusOS.RECEBIDA);
        assertThat(os.getDataAbertura()).isEqualTo(T0);
        assertThat(os.getHistorico()).hasSize(1);
        assertThat(os.getHistorico().get(0).de()).isNull();
        assertThat(os.getHistorico().get(0).para()).isEqualTo(StatusOS.RECEBIDA);
    }

    @Test
    void aberturaValidaCampos() {
        assertThatThrownBy(() -> OrdemServico.abrir(" ", "52998224725", 1L, "x", null, T0))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Número");
        assertThatThrownBy(() -> OrdemServico.abrir("OS-1", null, 1L, "x", null, T0))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Cliente");
        assertThatThrownBy(() -> OrdemServico.abrir("OS-1", "52998224725", 1L, " ", null, T0))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Descrição");
    }

    @Test
    void atribuiIdDepoisDePersistir() {
        os.atribuirId(42L);
        assertThat(os.getId()).isEqualTo(42L);
    }

    // ---------------------------------------------------------------- itens e total

    @Test
    void somaServicosEPecas() {
        os.adicionarServico(troca, 2);
        os.adicionarPeca(filtro, 3);
        // 2 x 120,50 + 3 x 35,00 = 346,00
        assertThat(os.valorTotal()).isEqualByComparingTo("346.00");
        assertThat(os.getItensServico()).hasSize(1);
        assertThat(os.getItensPeca().get(0).codigo()).isEqualTo("FLT-01");
    }

    @Test
    void osSemItensTemTotalZero() {
        assertThat(os.valorTotal()).isEqualByComparingTo("0");
    }

    @Test
    void itemGuardaOPrecoDoMomento() {
        os.adicionarServico(troca, 1);
        ItemServicoOS item = os.getItensServico().get(0);
        assertThat(item.precoUnitario()).isEqualByComparingTo("120.50");
        assertThat(item.subtotal()).isEqualByComparingTo("120.50");
    }

    @Test
    void rejeitaQuantidadeInvalida() {
        assertThatThrownBy(() -> os.adicionarServico(troca, 0))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Quantidade");
        assertThatThrownBy(() -> os.adicionarPeca(filtro, -1))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Quantidade");
    }

    @Test
    void rejeitaItemInativo() {
        assertThatThrownBy(() -> os.adicionarServico(troca.desativar(), 1))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("inativo");
        assertThatThrownBy(() -> os.adicionarPeca(filtro.desativar(), 1))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("inativa");
    }

    @Test
    void naoEditaItensDepoisDoOrcamento() {
        os.iniciarDiagnostico(T0);
        os.adicionarServico(troca, 1);
        os.aguardarAprovacao("ORC-1", T0);
        assertThatThrownBy(() -> os.adicionarPeca(filtro, 1))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Itens só podem");
        assertThatThrownBy(() -> os.adicionarServico(troca, 1))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void listasExpostasSaoImutaveis() {
        assertThatThrownBy(() -> os.getItensPeca().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> os.getItensServico().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> os.getHistorico().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    // ---------------------------------------------------------------- validar para orçamento

    @Test
    void orcamentoExigeDiagnostico() {
        os.adicionarServico(troca, 1);
        assertThatThrownBy(os::validarParaOrcamento)
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Em Diagnóstico");
    }

    @Test
    void orcamentoExigeAoMenosUmItem() {
        os.iniciarDiagnostico(T0);
        assertThatThrownBy(os::validarParaOrcamento)
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("ao menos um");
    }

    @Test
    void validarParaOrcamentoNaoMudaOStatus() {
        os.iniciarDiagnostico(T0);
        os.adicionarPeca(filtro, 1);
        os.validarParaOrcamento();
        assertThat(os.getStatus()).isEqualTo(StatusOS.EM_DIAGNOSTICO);
    }

    // ---------------------------------------------------------------- fluxo feliz

    @Test
    void percorreOFluxoCompletoAteAEntrega() {
        os.iniciarDiagnostico(T0);
        os.adicionarServico(troca, 1);
        os.aguardarAprovacao("ORC-1", T0);
        assertThat(os.getStatus()).isEqualTo(StatusOS.AGUARDANDO_APROVACAO);
        assertThat(os.getOrcamentoId()).isEqualTo("ORC-1");

        os.iniciarExecucao(T0.plusSeconds(60));
        assertThat(os.getStatus()).isEqualTo(StatusOS.EM_EXECUCAO);
        assertThat(os.getDataInicio()).isEqualTo(T0.plusSeconds(60));

        os.finalizar(T0.plusSeconds(120));
        assertThat(os.getStatus()).isEqualTo(StatusOS.FINALIZADA);
        assertThat(os.getDataFinalizacao()).isEqualTo(T0.plusSeconds(120));

        os.entregar(T0.plusSeconds(180));
        assertThat(os.getStatus()).isEqualTo(StatusOS.ENTREGUE);
        assertThat(os.getDataEntrega()).isEqualTo(T0.plusSeconds(180));

        // abertura + 5 transições
        assertThat(os.getHistorico()).hasSize(6);
        assertThat(os.getHistorico().get(5).de()).isEqualTo(StatusOS.FINALIZADA);
        assertThat(os.getHistorico().get(5).para()).isEqualTo(StatusOS.ENTREGUE);
    }

    @Test
    void naoPulaEtapas() {
        assertThatThrownBy(() -> os.finalizar(T0)).isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessageContaining("Recebida").hasMessageContaining("Finalizada");
        assertThatThrownBy(() -> os.iniciarExecucao(T0)).isInstanceOf(TransicaoStatusInvalidaException.class);
        assertThatThrownBy(() -> os.entregar(T0)).isInstanceOf(TransicaoStatusInvalidaException.class);
        assertThat(os.getStatus()).isEqualTo(StatusOS.RECEBIDA);
        assertThat(os.getHistorico()).hasSize(1);
    }

    // ---------------------------------------------------------------- recusa e compensações

    @Test
    void recusaVoltaAoDiagnosticoELimpaOOrcamento() {
        os.iniciarDiagnostico(T0);
        os.adicionarServico(troca, 1);
        os.aguardarAprovacao("ORC-1", T0);

        os.voltarParaDiagnostico("Cliente recusou", T0.plusSeconds(30));

        assertThat(os.getStatus()).isEqualTo(StatusOS.EM_DIAGNOSTICO);
        assertThat(os.getOrcamentoId()).isNull();
        HistoricoStatus ultimo = os.getHistorico().get(os.getHistorico().size() - 1);
        assertThat(ultimo.motivo()).isEqualTo("Cliente recusou");
    }

    @Test
    void podeGerarNovoOrcamentoDepoisDaRecusa() {
        os.iniciarDiagnostico(T0);
        os.adicionarServico(troca, 1);
        os.aguardarAprovacao("ORC-1", T0);
        os.voltarParaDiagnostico("Pagamento recusado", T0);

        os.adicionarPeca(filtro, 1);
        os.validarParaOrcamento();
        os.aguardarAprovacao("ORC-2", T0);

        assertThat(os.getOrcamentoId()).isEqualTo("ORC-2");
    }

    @Test
    void cancelaOSEmExecucaoCompensandoASaga() {
        osEmExecucao().cancelar("Estoque insuficiente", T0.plusSeconds(90));

        assertThat(os.getStatus()).isEqualTo(StatusOS.CANCELADA);
        assertThat(os.getMotivoCancelamento()).isEqualTo("Estoque insuficiente");
    }

    @Test
    void cancelamentoExigeMotivo() {
        assertThatThrownBy(() -> os.cancelar(" ", T0))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Motivo");
        assertThatThrownBy(() -> os.cancelar(null, T0))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoCancelaOSJaFinalizadaOuEntregue() {
        osEmExecucao().finalizar(T0);
        assertThatThrownBy(() -> os.cancelar("tarde demais", T0))
                .isInstanceOf(TransicaoStatusInvalidaException.class);
        os.entregar(T0);
        assertThatThrownBy(() -> os.cancelar("tarde demais", T0))
                .isInstanceOf(TransicaoStatusInvalidaException.class);
    }

    @Test
    void cancelaOSRecebidaSemCompensacao() {
        os.cancelar("Cliente desistiu", T0);
        assertThat(os.getStatus()).isEqualTo(StatusOS.CANCELADA);
    }

    // ---------------------------------------------------------------- reconstituição

    @Test
    void reconstituiAPartirDosDadosPersistidos() {
        OrdemServico recarregada = OrdemServico.reconstituir(new OrdemServico.Dados(
                7L, "OS-2026-7", "52998224725", 2L, StatusOS.EM_EXECUCAO, "Freio", "obs", "ORC-7", null,
                List.of(new ItemServicoOS(1L, "Alinhamento", 1, new BigDecimal("10.00"))),
                List.of(), List.of(new HistoricoStatus(null, StatusOS.RECEBIDA, T0, "OS aberta")),
                T0, T0.plusSeconds(10), null, null));

        assertThat(recarregada.getId()).isEqualTo(7L);
        assertThat(recarregada.getOrcamentoId()).isEqualTo("ORC-7");
        assertThat(recarregada.valorTotal()).isEqualByComparingTo("10.00");
        assertThat(recarregada.getObservacoes()).isEqualTo("obs");
        assertThat(recarregada.getVeiculoId()).isEqualTo(2L);
        assertThat(recarregada.getDescricaoProblema()).isEqualTo("Freio");
        assertThat(recarregada.getDataInicio()).isEqualTo(T0.plusSeconds(10));

        recarregada.finalizar(T0.plusSeconds(20));
        assertThat(recarregada.getStatus()).isEqualTo(StatusOS.FINALIZADA);
    }
}
