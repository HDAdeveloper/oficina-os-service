package br.com.oficina.os.infrastructure.persistence;

import br.com.oficina.os.application.port.in.GestaoClientes;
import br.com.oficina.os.application.port.in.GestaoClientes.NovoCliente;
import br.com.oficina.os.application.port.in.GestaoOrdensServico;
import br.com.oficina.os.application.port.in.GestaoOrdensServico.AberturaOS;
import br.com.oficina.os.application.port.in.GestaoOrdensServico.ItemSolicitado;
import br.com.oficina.os.application.port.in.GestaoPecas;
import br.com.oficina.os.application.port.in.GestaoPecas.NovaPeca;
import br.com.oficina.os.application.port.in.GestaoServicos;
import br.com.oficina.os.application.port.in.GestaoServicos.NovoServico;
import br.com.oficina.os.application.port.in.GestaoVeiculos;
import br.com.oficina.os.application.port.in.GestaoVeiculos.NovoVeiculo;
import br.com.oficina.os.domain.exception.ConflitoException;
import br.com.oficina.os.domain.model.Cliente;
import br.com.oficina.os.domain.model.OrdemServico;
import br.com.oficina.os.domain.model.Peca;
import br.com.oficina.os.domain.model.Servico;
import br.com.oficina.os.domain.model.StatusOS;
import br.com.oficina.os.domain.model.Veiculo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contexto Spring completo com banco real: confere a fiação dos casos de uso, o proxy de transação
 * e o bloqueio otimista. Não usa @Transactional no teste, então cada chamada confirma de verdade.
 */
@SpringBootTest
class FluxoComBancoTest extends PostgresTestBase {

    @Autowired GestaoClientes clientes;
    @Autowired GestaoVeiculos veiculos;
    @Autowired GestaoServicos servicos;
    @Autowired GestaoPecas pecas;
    @Autowired GestaoOrdensServico ordens;
    @Autowired OrdemServicoJpa ordemJpa;
    @Autowired TransactionTemplate tx;

    @Test
    void fluxoCompletoDeAberturaDeOsGravaNoBanco() {
        Cliente c = clientes.cadastrar(new NovoCliente("Carlos", "390.533.447-05", "c@x.com", null, null));
        Veiculo v = veiculos.cadastrar(new NovoVeiculo("FLX1A23", "Fiat", "Argo", 2021, c.id()));
        Servico s = servicos.cadastrar(new NovoServico("Alinhamento", null, new BigDecimal("90.00"), 45));
        Peca p = pecas.cadastrar(new NovaPeca("FLX-PASTILHA", "Pastilha de freio", null, new BigDecimal("80.00")));

        OrdemServico os = ordens.abrir(new AberturaOS("39053344705", "flx1a23", "Freio rangendo", null,
                List.of(new ItemSolicitado(s.id(), 1)), List.of(new ItemSolicitado(p.id(), 2))));
        ordens.iniciarDiagnostico(os.getId());
        ordens.solicitarOrcamento(os.getId());

        OrdemServico lida = ordens.buscarPorId(os.getId());
        assertThat(lida.getNumero()).matches("OS-\\d{4}-[0-9A-F]{8}");
        assertThat(lida.getStatus()).isEqualTo(StatusOS.EM_DIAGNOSTICO);
        assertThat(lida.valorTotal()).isEqualByComparingTo("250.00");
        assertThat(ordens.consultarHistorico(os.getId())).hasSize(2);
        assertThat(ordens.listarAtivas()).extracting(OrdemServico::getId).contains(os.getId());
        assertThat(v.id()).isNotNull();
    }

    @Test
    void erroDeNegocioDesfazATransacao() {
        clientes.cadastrar(new NovoCliente("Dora", "123.456.789-09", null, null, null));
        assertThatThrownBy(() -> clientes.cadastrar(new NovoCliente("Dora 2", "12345678909", null, null, null)))
                .isInstanceOf(ConflitoException.class);
        assertThat(clientes.listar()).filteredOn(c -> c.documento().numero().equals("12345678909")).hasSize(1);
    }

    @Test
    void bloqueioOtimistaImpedeSobrescreverOsAlteradaPorOutro() {
        Cliente c = clientes.cadastrar(new NovoCliente("Eva", "987.654.321-00", null, null, null));
        veiculos.cadastrar(new NovoVeiculo("LCK1A23", "VW", "Polo", 2023, c.id()));
        OrdemServico os = ordens.abrir(new AberturaOS("98765432100", "LCK1A23", "Teste", null, null, null));

        // Leitura "antiga": a entidade sai da transação com a versão atual.
        OrdemServicoEntity antiga = tx.execute(status -> {
            OrdemServicoEntity e = ordemJpa.findById(os.getId()).orElseThrow();
            e.itensServico.size();
            e.itensPeca.size();
            e.historico.size();
            return e;
        });

        // Outra pessoa altera a mesma OS e a versão no banco avança.
        ordens.iniciarDiagnostico(os.getId());

        antiga.observacoes = "gravação com dado velho";
        assertThatThrownBy(() -> ordemJpa.saveAndFlush(antiga))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
