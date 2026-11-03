package br.com.oficina.os.infrastructure.persistence;

import br.com.oficina.os.domain.model.Cliente;
import br.com.oficina.os.domain.model.EstadoSaga;
import br.com.oficina.os.domain.model.OrdemServico;
import br.com.oficina.os.domain.model.Saga;
import br.com.oficina.os.domain.model.Veiculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ClienteRepositoryAdapter.class, VeiculoRepositoryAdapter.class, OrdemServicoRepositoryAdapter.class,
        SagaRepositoryAdapter.class, MensagemProcessadaRepositoryAdapter.class})
class SagaPersistenciaTest extends PostgresTestBase {

    private static final Instant T0 = Instant.parse("2026-10-23T12:00:00Z");

    @Autowired ClienteRepositoryAdapter clientes;
    @Autowired VeiculoRepositoryAdapter veiculos;
    @Autowired OrdemServicoRepositoryAdapter ordens;
    @Autowired SagaRepositoryAdapter sagas;
    @Autowired MensagemProcessadaRepositoryAdapter processadas;
    @Autowired TestEntityManager em;

    private Long osId;

    @BeforeEach
    void setUp() {
        Cliente c = clientes.salvar(Cliente.novo("Ana Souza", "529.982.247-25", "ana@exemplo.com", null, null));
        Veiculo v = veiculos.salvar(Veiculo.novo("SAG1A23", "Chevrolet", "Onix", 2022, c.id()));
        osId = ordens.salvar(OrdemServico.abrir("OS-SAGA-1", c.id(), v.id(), "Revisão", null, T0)).getId();
    }

    private void limparCache() {
        em.flush();
        em.clear();
    }

    @Test
    void salvaELeASagaComODiarioEmOrdem() {
        Saga saga = Saga.iniciar("saga-1", osId, "corr-1", T0, T0.plusSeconds(60));
        saga.registrarOrcamento("orc-1", new BigDecimal("220.50"));
        saga.registrarMetodoPagamento("APRO");
        saga.mover(EstadoSaga.AGUARDANDO_APROVACAO, "Orçamento orc-1", T0.plusSeconds(5), null);
        sagas.salvar(saga);
        limparCache();

        Saga lida = sagas.buscarPorId("saga-1").orElseThrow();

        assertThat(lida.getOsId()).isEqualTo(osId);
        assertThat(lida.getCorrelationId()).isEqualTo("corr-1");
        assertThat(lida.getEstado()).isEqualTo(EstadoSaga.AGUARDANDO_APROVACAO);
        assertThat(lida.getOrcamentoId()).isEqualTo("orc-1");
        assertThat(lida.getValorTotal()).isEqualByComparingTo("220.50");
        assertThat(lida.getMetodoPagamento()).isEqualTo("APRO");
        assertThat(lida.getPrazo()).isNull();
        assertThat(lida.getCriadaEm()).isEqualTo(T0);
        assertThat(lida.getPassos()).extracting(p -> p.tipo()).containsExactly("SAGA_INICIADA", "AGUARDANDO_APROVACAO");
        assertThat(sagas.buscarPorId("nao-existe")).isEmpty();
    }

    @Test
    void guardaOLinkDePagamentoEOPrazoDeEspera() {
        Saga saga = Saga.iniciar("saga-link", osId, "corr-1", T0, T0.plusSeconds(60));
        saga.registrarPagamento("pag-1");
        saga.registrarLink("https://mp/pagar/abc");
        saga.mover(EstadoSaga.AGUARDANDO_PAGAMENTO, "Link gerado", T0.plusSeconds(5), T0.plusSeconds(3660));
        sagas.salvar(saga);
        limparCache();

        Saga lida = sagas.buscarPorId("saga-link").orElseThrow();

        assertThat(lida.getEstado()).isEqualTo(EstadoSaga.AGUARDANDO_PAGAMENTO);
        assertThat(lida.getLinkPagamento()).isEqualTo("https://mp/pagar/abc");
        assertThat(lida.getPagamentoId()).isEqualTo("pag-1");
        assertThat(lida.getPrazo()).isEqualTo(T0.plusSeconds(3660));
    }

    @Test
    void atualizaASagaExistenteSemDuplicar() {
        sagas.salvar(Saga.iniciar("saga-2", osId, "corr", T0, T0.plusSeconds(60)));
        limparCache();
        Saga lida = sagas.buscarPorId("saga-2").orElseThrow();
        lida.registrarOrcamento("orc-2", new BigDecimal("10.00"));
        lida.mover(EstadoSaga.AGUARDANDO_APROVACAO, "ok", T0.plusSeconds(1), null);
        lida.anotar("EVENTO", "OrcamentoCriado", T0.plusSeconds(1));
        sagas.salvar(lida);
        limparCache();

        Saga depois = sagas.buscarPorId("saga-2").orElseThrow();

        assertThat(depois.getEstado()).isEqualTo(EstadoSaga.AGUARDANDO_APROVACAO);
        assertThat(depois.getPassos()).hasSize(3);
        assertThat(depois.getVersao()).isGreaterThanOrEqualTo(1);
        assertThat(sagas.buscarPorOs(osId)).hasSize(1);
    }

    @Test
    void listaAsSagasDaOsDaMaisRecenteParaAMaisAntiga() {
        sagas.salvar(Saga.iniciar("antiga", osId, "c", T0, T0.plusSeconds(60)));
        sagas.salvar(Saga.iniciar("nova", osId, "c", T0.plusSeconds(600), T0.plusSeconds(660)));
        limparCache();

        List<Saga> lista = sagas.buscarPorOs(osId);

        assertThat(lista).extracting(Saga::getId).containsExactly("nova", "antiga");
        assertThat(sagas.buscarPorOs(-1L)).isEmpty();
    }

    @Test
    void buscaAsSagasMaisRecentementeAtualizadasLimitandoAQuantidade() {
        // o banco é compartilhado com outros testes: datas bem no futuro garantem que estas sejam as mais recentes
        Instant base = Instant.now().plus(java.time.Duration.ofDays(3650));
        sagas.salvar(Saga.iniciar("a1", osId, "c", base, base.plusSeconds(60)));
        sagas.salvar(Saga.iniciar("a2", osId, "c", base.plusSeconds(10), base.plusSeconds(70)));
        sagas.salvar(Saga.iniciar("a3", osId, "c", base.plusSeconds(20), base.plusSeconds(80)));
        limparCache();

        assertThat(sagas.buscarRecentes(2)).extracting(Saga::getId).containsExactly("a3", "a2");
        assertThat(sagas.buscarRecentes(100)).extracting(Saga::getId).contains("a1", "a2", "a3");
    }

    @Test
    void achaSoAsSagasComPrazoVencido() {
        sagas.salvar(Saga.iniciar("vencida", osId, "c", T0, T0.plusSeconds(60)));
        Saga futura = Saga.iniciar("futura", osId, "c", T0, T0.plusSeconds(3600));
        sagas.salvar(futura);
        Saga semPrazo = Saga.iniciar("sem-prazo", osId, "c", T0, T0.plusSeconds(60));
        semPrazo.mover(EstadoSaga.AGUARDANDO_APROVACAO, "x", T0, null);
        sagas.salvar(semPrazo);
        limparCache();

        List<Saga> vencidas = sagas.buscarComPrazoVencido(T0.plusSeconds(120));

        assertThat(vencidas).extracting(Saga::getId).contains("vencida").doesNotContain("futura", "sem-prazo");
    }

    @Test
    void mensagemSoEhRegistradaUmaVez() {
        assertThat(processadas.registrar("msg-1", T0)).isTrue();
        assertThat(processadas.registrar("msg-1", T0.plusSeconds(5))).isFalse();
        assertThat(processadas.registrar("msg-2", T0)).isTrue();
    }

    @Test
    void bancoImpedeSagaDeOsInexistente() {
        assertThatThrownBy(() -> sagas.salvar(Saga.iniciar("orfa", 999_999L, "c", T0, T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
