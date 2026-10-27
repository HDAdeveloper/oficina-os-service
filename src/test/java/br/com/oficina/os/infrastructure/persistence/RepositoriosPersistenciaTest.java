package br.com.oficina.os.infrastructure.persistence;

import br.com.oficina.os.domain.model.Cliente;
import br.com.oficina.os.domain.model.HistoricoStatus;
import br.com.oficina.os.domain.model.OrdemServico;
import br.com.oficina.os.domain.model.Peca;
import br.com.oficina.os.domain.model.Servico;
import br.com.oficina.os.domain.model.StatusOS;
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
@Import({ClienteRepositoryAdapter.class, VeiculoRepositoryAdapter.class, ServicoRepositoryAdapter.class,
        PecaRepositoryAdapter.class, OrdemServicoRepositoryAdapter.class})
class RepositoriosPersistenciaTest extends PostgresTestBase {

    private static final Instant T0 = Instant.parse("2026-10-22T12:00:00Z");

    @Autowired ClienteRepositoryAdapter clientes;
    @Autowired VeiculoRepositoryAdapter veiculos;
    @Autowired ServicoRepositoryAdapter servicos;
    @Autowired PecaRepositoryAdapter pecas;
    @Autowired OrdemServicoRepositoryAdapter ordens;
    @Autowired TestEntityManager em;

    private Cliente cliente;
    private Veiculo veiculo;

    @BeforeEach
    void setUp() {
        cliente = clientes.salvar(Cliente.novo("Ana Souza", "529.982.247-25", "ana@exemplo.com", "11999999999", "Rua A, 1"));
        veiculo = veiculos.salvar(Veiculo.novo("abc-1d23", "Chevrolet", "Onix", 2022, cliente.id()));
    }

    /** Força o próximo acesso a ir ao banco, provando que o dado foi mesmo gravado. */
    private void limparCache() {
        em.flush();
        em.clear();
    }

    // ------------------------------------------------------------ cliente

    @Test
    void salvaEBuscaCliente() {
        assertThat(cliente.id()).isNotNull();
        limparCache();

        Cliente lido = clientes.buscarPorDocumento("52998224725").orElseThrow();
        assertThat(lido.id()).isEqualTo(cliente.id());
        assertThat(lido.nome()).isEqualTo("Ana Souza");
        assertThat(lido.documento().formatado()).isEqualTo("529.982.247-25");
        assertThat(lido.ativo()).isTrue();
        assertThat(clientes.buscarPorId(cliente.id())).isPresent();
        assertThat(clientes.buscarPorId(-1L)).isEmpty();
    }

    @Test
    void atualizaEDesativaCliente() {
        clientes.salvar(cliente.atualizar("Ana Maria", "ana@x.com", null, null));
        clientes.salvar(clientes.buscarPorId(cliente.id()).orElseThrow().desativar());
        limparCache();

        Cliente lido = clientes.buscarPorId(cliente.id()).orElseThrow();
        assertThat(lido.nome()).isEqualTo("Ana Maria");
        assertThat(lido.ativo()).isFalse();
        assertThat(clientes.listarAtivos()).extracting(Cliente::id).doesNotContain(cliente.id());
    }

    @Test
    void bancoImpedeDocumentoDuplicado() {
        assertThatThrownBy(() -> clientes.salvar(Cliente.novo("Outra Ana", "52998224725", null, null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------ veículo

    @Test
    void salvaEBuscaVeiculo() {
        limparCache();
        Veiculo lido = veiculos.buscarPorPlaca("ABC1D23").orElseThrow();
        assertThat(lido.id()).isEqualTo(veiculo.id());
        assertThat(lido.clienteId()).isEqualTo(cliente.id());
        assertThat(veiculos.listarPorCliente(cliente.id())).hasSize(1);
        assertThat(veiculos.listarAtivos()).extracting(Veiculo::id).contains(veiculo.id());
        assertThat(veiculos.buscarPorId(veiculo.id())).isPresent();

        veiculos.salvar(lido.desativar());
        limparCache();
        assertThat(veiculos.listarAtivos()).extracting(Veiculo::id).doesNotContain(veiculo.id());
    }

    @Test
    void bancoImpedeVeiculoDeClienteInexistente() {
        assertThatThrownBy(() -> veiculos.salvar(Veiculo.novo("XYZ9A99", "VW", "Gol", 2020, 999_999L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------ catálogo

    @Test
    void salvaEBuscaServicoMantendoPreco() {
        Servico s = servicos.salvar(Servico.novo("Troca de óleo", "5W30", new BigDecimal("150.50"), 30));
        limparCache();

        Servico lido = servicos.buscarPorId(s.id()).orElseThrow();
        assertThat(lido.preco()).isEqualByComparingTo("150.50");
        assertThat(lido.tempoEstimadoMinutos()).isEqualTo(30);
        assertThat(servicos.listarAtivos()).extracting(Servico::id).contains(s.id());

        servicos.salvar(lido.desativar());
        limparCache();
        assertThat(servicos.listarAtivos()).extracting(Servico::id).doesNotContain(s.id());
    }

    @Test
    void salvaEBuscaPecaPorCodigoEImpedeDuplicado() {
        Peca p = pecas.salvar(Peca.nova("FLT-01", "Filtro de óleo", null, new BigDecimal("35.00")));
        limparCache();

        assertThat(pecas.buscarPorCodigo("FLT-01").orElseThrow().id()).isEqualTo(p.id());
        assertThat(pecas.buscarPorId(p.id())).isPresent();
        assertThat(pecas.listarAtivas()).extracting(Peca::id).contains(p.id());
        assertThatThrownBy(() -> pecas.salvar(Peca.nova("FLT-01", "Outro", null, BigDecimal.ONE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------ ordem de serviço

    @Test
    void salvaOsComItensEHistoricoEReconstroiIgual() {
        Servico s = servicos.salvar(Servico.novo("Troca de óleo", null, new BigDecimal("150.00"), 30));
        Peca p = pecas.salvar(Peca.nova("FLT-02", "Filtro", null, new BigDecimal("35.00")));

        OrdemServico os = OrdemServico.abrir("OS-2026-AAAA0001", cliente.id(), veiculo.id(), "Barulho", "urgente", T0);
        os.adicionarServico(s, 1);
        os.adicionarPeca(p, 2);
        os.iniciarDiagnostico(T0.plusSeconds(60));
        os.aguardarAprovacao("orc-1", T0.plusSeconds(90));
        os.iniciarExecucao(T0.plusSeconds(120));
        os.finalizar(T0.plusSeconds(180));
        ordens.salvar(os);
        assertThat(os.getId()).isNotNull();
        limparCache();

        OrdemServico lida = ordens.buscarPorId(os.getId()).orElseThrow();
        assertThat(lida.getNumero()).isEqualTo("OS-2026-AAAA0001");
        assertThat(lida.getStatus()).isEqualTo(StatusOS.FINALIZADA);
        assertThat(lida.getObservacoes()).isEqualTo("urgente");
        assertThat(lida.getOrcamentoId()).isEqualTo("orc-1");
        assertThat(lida.getDataAbertura()).isEqualTo(T0);
        assertThat(lida.getDataInicio()).isEqualTo(T0.plusSeconds(120));
        assertThat(lida.getDataFinalizacao()).isEqualTo(T0.plusSeconds(180));
        assertThat(lida.getDataEntrega()).isNull();
        assertThat(lida.valorTotal()).isEqualByComparingTo("220.00");
        assertThat(lida.getItensServico()).hasSize(1);
        assertThat(lida.getItensPeca()).singleElement().satisfies(i -> {
            assertThat(i.codigo()).isEqualTo("FLT-02");
            assertThat(i.quantidade()).isEqualTo(2);
        });
        assertThat(lida.getHistorico()).extracting(HistoricoStatus::para)
                .containsExactly(StatusOS.RECEBIDA, StatusOS.EM_DIAGNOSTICO, StatusOS.AGUARDANDO_APROVACAO,
                        StatusOS.EM_EXECUCAO, StatusOS.FINALIZADA);
        assertThat(lida.getHistorico().get(0).de()).isNull();
    }

    @Test
    void atualizaOsExistenteSemDuplicarListas() {
        OrdemServico os = OrdemServico.abrir("OS-2026-AAAA0002", cliente.id(), veiculo.id(), "Freio", null, T0);
        ordens.salvar(os);
        limparCache();

        OrdemServico carregada = ordens.buscarPorId(os.getId()).orElseThrow();
        carregada.iniciarDiagnostico(T0.plusSeconds(10));
        carregada.aguardarAprovacao("orc-77", T0.plusSeconds(20));
        ordens.salvar(carregada);
        limparCache();

        OrdemServico lida = ordens.buscarPorId(os.getId()).orElseThrow();
        assertThat(lida.getStatus()).isEqualTo(StatusOS.AGUARDANDO_APROVACAO);
        assertThat(lida.getOrcamentoId()).isEqualTo("orc-77");
        assertThat(lida.getHistorico()).hasSize(3);
    }

    @Test
    void cancelamentoGravaMotivo() {
        OrdemServico os = OrdemServico.abrir("OS-2026-AAAA0003", cliente.id(), veiculo.id(), "Pane", null, T0);
        os.cancelar("desistiu", T0.plusSeconds(5));
        ordens.salvar(os);
        limparCache();

        OrdemServico lida = ordens.buscarPorId(os.getId()).orElseThrow();
        assertThat(lida.getStatus()).isEqualTo(StatusOS.CANCELADA);
        assertThat(lida.getMotivoCancelamento()).isEqualTo("desistiu");
    }

    @Test
    void buscaPorStatus() {
        OrdemServico a = OrdemServico.abrir("OS-2026-AAAA0004", cliente.id(), veiculo.id(), "A", null, T0);
        OrdemServico b = OrdemServico.abrir("OS-2026-AAAA0005", cliente.id(), veiculo.id(), "B", null, T0);
        b.iniciarDiagnostico(T0.plusSeconds(1));
        ordens.salvar(a);
        ordens.salvar(b);
        limparCache();

        // O banco é compartilhado com outros testes que confirmam dados, então não assumimos que só
        // existem estas duas OS: conferimos apenas a presença/ausência das nossas.
        assertThat(ordens.buscarPorStatus(List.of(StatusOS.RECEBIDA))).extracting(OrdemServico::getId)
                .contains(a.getId()).doesNotContain(b.getId());
        assertThat(ordens.buscarPorStatus(List.of(StatusOS.EM_DIAGNOSTICO))).extracting(OrdemServico::getId)
                .contains(b.getId()).doesNotContain(a.getId());
        assertThat(ordens.buscarPorStatus(List.of(StatusOS.RECEBIDA, StatusOS.EM_DIAGNOSTICO)))
                .extracting(OrdemServico::getId).contains(a.getId(), b.getId());
        assertThat(ordens.buscarPorId(-1L)).isEmpty();
    }

    @Test
    void bancoImpedeNumeroDeOsDuplicado() {
        ordens.salvar(OrdemServico.abrir("OS-2026-AAAA0006", cliente.id(), veiculo.id(), "A", null, T0));
        assertThatThrownBy(() -> ordens.salvar(
                OrdemServico.abrir("OS-2026-AAAA0006", cliente.id(), veiculo.id(), "B", null, T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
