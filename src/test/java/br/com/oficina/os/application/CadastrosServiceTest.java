package br.com.oficina.os.application;

import br.com.oficina.os.application.port.in.GestaoClientes.AlteracaoCliente;
import br.com.oficina.os.application.port.in.GestaoClientes.NovoCliente;
import br.com.oficina.os.application.port.in.GestaoPecas.AlteracaoPeca;
import br.com.oficina.os.application.port.in.GestaoPecas.NovaPeca;
import br.com.oficina.os.application.port.in.GestaoServicos.AlteracaoServico;
import br.com.oficina.os.application.port.in.GestaoServicos.NovoServico;
import br.com.oficina.os.application.port.in.GestaoVeiculos.AlteracaoVeiculo;
import br.com.oficina.os.application.port.in.GestaoVeiculos.NovoVeiculo;
import br.com.oficina.os.application.service.ClienteService;
import br.com.oficina.os.application.service.PecaService;
import br.com.oficina.os.application.service.ServicoService;
import br.com.oficina.os.application.service.VeiculoService;
import br.com.oficina.os.domain.exception.ConflitoException;
import br.com.oficina.os.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.os.domain.exception.RegraDeNegocioException;
import br.com.oficina.os.domain.model.Cliente;
import br.com.oficina.os.domain.model.Peca;
import br.com.oficina.os.domain.model.Servico;
import br.com.oficina.os.domain.model.Veiculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CadastrosServiceTest {

    private static final String CPF = "529.982.247-25";

    private Fakes.ClientesMem clientesRepo;
    private Fakes.VeiculosMem veiculosRepo;
    private ClienteService clientes;
    private VeiculoService veiculos;
    private ServicoService servicos;
    private PecaService pecas;

    @BeforeEach
    void setUp() {
        clientesRepo = new Fakes.ClientesMem();
        veiculosRepo = new Fakes.VeiculosMem();
        clientes = new ClienteService(clientesRepo);
        veiculos = new VeiculoService(veiculosRepo, clientesRepo);
        servicos = new ServicoService(new Fakes.ServicosMem());
        pecas = new PecaService(new Fakes.PecasMem());
    }

    private Cliente novoCliente() {
        return clientes.cadastrar(new NovoCliente("Ana Souza", CPF, "ana@exemplo.com", "11999999999", "Rua A, 1"));
    }

    // ------------------------------------------------------------ clientes

    @Test
    void cadastraClienteComODocumentoComoId() {
        Cliente c = novoCliente();
        assertThat(c.id()).isEqualTo("52998224725");
        assertThat(clientes.buscarPorDocumento("52998224725").id()).isEqualTo(c.id());
        assertThat(clientes.buscarPorDocumento(CPF).id()).isEqualTo(c.id());
    }

    @Test
    void rejeitaDocumentoDuplicado() {
        novoCliente();
        assertThatThrownBy(this::novoCliente).isInstanceOf(ConflitoException.class);
    }

    @Test
    void atualizaEDesativaCliente() {
        Cliente c = novoCliente();
        Cliente atualizado = clientes.atualizar(c.id(), new AlteracaoCliente("Ana Maria", "ana@x.com", null, null));
        assertThat(atualizado.nome()).isEqualTo("Ana Maria");

        clientes.desativar(c.id());
        assertThat(clientes.listar()).isEmpty();
        assertThat(clientes.buscarPorId(c.id()).ativo()).isFalse();
    }

    @Test
    void reativaClienteDesativadoERecebeNovasOrdens() {
        Cliente c = novoCliente();
        clientes.desativar(c.id());

        Cliente reativado = clientes.reativar(CPF);

        assertThat(reativado.ativo()).isTrue();
        assertThat(clientes.listar()).extracting(Cliente::id).containsExactly(c.id());
        assertThat(clientes.reativar(c.id()).ativo()).isTrue(); // já ativo: não muda nada
    }

    @Test
    void cadastrarDocumentoDeClienteInativoOrientaAReativacao() {
        Cliente c = novoCliente();
        clientes.desativar(c.id());

        assertThatThrownBy(this::novoCliente).isInstanceOf(ConflitoException.class)
                .hasMessageContaining("reativação");
    }

    @Test
    void reativarClienteInexistenteGeraNaoEncontrado() {
        assertThatThrownBy(() -> clientes.reativar("99")).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void clienteInexistenteGeraNaoEncontrado() {
        assertThatThrownBy(() -> clientes.buscarPorId("99")).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> clientes.buscarPorDocumento("52998224725"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // ------------------------------------------------------------ veículos

    @Test
    void cadastraVeiculoDoCliente() {
        Cliente c = novoCliente();
        Veiculo v = veiculos.cadastrar(new NovoVeiculo("abc-1d23", "Chevrolet", "Onix", 2022, c.id()));
        assertThat(v.id()).isNotNull();
        assertThat(veiculos.buscarPorPlaca("ABC-1D23").id()).isEqualTo(v.id());
        assertThat(veiculos.listarPorCliente(c.id())).hasSize(1);
        assertThat(veiculos.listar()).hasSize(1);
    }

    @Test
    void rejeitaVeiculoSemClienteOuComClienteInexistenteOuInativo() {
        assertThatThrownBy(() -> veiculos.cadastrar(new NovoVeiculo("ABC1D23", "VW", "Gol", 2020, null)))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> veiculos.cadastrar(new NovoVeiculo("ABC1D23", "VW", "Gol", 2020, "42")))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        Cliente c = novoCliente();
        clientes.desativar(c.id());
        assertThatThrownBy(() -> veiculos.cadastrar(new NovoVeiculo("ABC1D23", "VW", "Gol", 2020, c.id())))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void rejeitaPlacaDuplicada() {
        Cliente c = novoCliente();
        veiculos.cadastrar(new NovoVeiculo("ABC1D23", "VW", "Gol", 2020, c.id()));
        assertThatThrownBy(() -> veiculos.cadastrar(new NovoVeiculo("abc1d23", "Fiat", "Uno", 2015, c.id())))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void atualizaEDesativaVeiculo() {
        Cliente c = novoCliente();
        Veiculo v = veiculos.cadastrar(new NovoVeiculo("ABC1D23", "VW", "Gol", 2020, c.id()));
        assertThat(veiculos.atualizar(v.id(), new AlteracaoVeiculo("VW", "Polo", 2021)).modelo()).isEqualTo("Polo");
        veiculos.desativar(v.id());
        assertThat(veiculos.listar()).isEmpty();
    }

    @Test
    void veiculoInexistenteGeraNaoEncontrado() {
        assertThatThrownBy(() -> veiculos.buscarPorId(1L)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> veiculos.buscarPorPlaca("ABC1D23")).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // ------------------------------------------------------------ serviços

    @Test
    void gerenciaCatalogoDeServicos() {
        Servico s = servicos.cadastrar(new NovoServico("Troca de óleo", "Óleo 5W30", new BigDecimal("150.00"), 30));
        assertThat(servicos.buscarPorId(s.id()).nome()).isEqualTo("Troca de óleo");

        Servico novo = servicos.atualizar(s.id(),
                new AlteracaoServico("Troca de óleo", "Óleo 5W40", new BigDecimal("170.00"), 40));
        assertThat(novo.preco()).isEqualByComparingTo("170.00");

        servicos.desativar(s.id());
        assertThat(servicos.listar()).isEmpty();
        assertThatThrownBy(() -> servicos.buscarPorId(77L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // ------------------------------------------------------------ peças

    @Test
    void gerenciaCatalogoDePecas() {
        Peca p = pecas.cadastrar(new NovaPeca("flt-01", "Filtro de óleo", null, new BigDecimal("35.00")));
        assertThat(p.codigo()).isEqualTo("FLT-01");
        assertThat(pecas.buscarPorCodigo(" flt-01 ").id()).isEqualTo(p.id());

        assertThat(pecas.atualizar(p.id(), new AlteracaoPeca("Filtro", "x", new BigDecimal("40.00")))
                .precoUnitario()).isEqualByComparingTo("40.00");

        pecas.desativar(p.id());
        assertThat(pecas.listar()).isEmpty();
    }

    @Test
    void pecaDuplicadaOuInexistente() {
        pecas.cadastrar(new NovaPeca("FLT-01", "Filtro", null, BigDecimal.TEN));
        assertThatThrownBy(() -> pecas.cadastrar(new NovaPeca("flt-01", "Outro", null, BigDecimal.ONE)))
                .isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> pecas.buscarPorId(99L)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> pecas.buscarPorCodigo("ZZZ")).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> pecas.buscarPorCodigo(null)).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
