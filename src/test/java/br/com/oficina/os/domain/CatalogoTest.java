package br.com.oficina.os.domain;

import br.com.oficina.os.domain.exception.RegraDeNegocioException;
import br.com.oficina.os.domain.model.Cliente;
import br.com.oficina.os.domain.model.Peca;
import br.com.oficina.os.domain.model.Servico;
import br.com.oficina.os.domain.model.Veiculo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cliente, Veículo, Serviço e Peça: validações e atualizações. */
class CatalogoTest {

    // ------------------------------------------------------------ Cliente

    @Test
    void criaClienteAtivoComDocumentoValido() {
        Cliente c = Cliente.novo("Ana Souza", "529.982.247-25", "ana@exemplo.com", "11999999999", "Rua A, 1");
        assertThat(c.ativo()).isTrue();
        assertThat(c.documento().numero()).isEqualTo("52998224725");
        assertThat(c.id()).isEqualTo("52998224725");
    }

    @Test
    void clienteExigeNome() {
        assertThatThrownBy(() -> Cliente.novo(" ", "52998224725", null, null, null))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Nome");
    }

    @Test
    void clienteRejeitaEmailInvalido() {
        assertThatThrownBy(() -> Cliente.novo("Ana", "52998224725", "ana@", null, null))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("E-mail");
    }

    @Test
    void clienteAceitaEmailEmBranco() {
        assertThat(Cliente.novo("Ana", "52998224725", " ", null, null).ativo()).isTrue();
    }

    @Test
    void clienteExigeDocumento() {
        assertThatThrownBy(() -> new Cliente(null, "Ana", null, null, null, true))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Documento");
    }

    @Test
    void desativarEAtualizarCliente() {
        Cliente c = Cliente.novo("Ana", "52998224725", "ana@exemplo.com", null, null);
        assertThat(c.desativar().ativo()).isFalse();
        Cliente atualizado = c.atualizar("Ana Maria", "novo@exemplo.com", "11888888888", "Rua B");
        assertThat(atualizado.nome()).isEqualTo("Ana Maria");
        assertThat(atualizado.documento()).isEqualTo(c.documento());
        assertThat(atualizado.email()).isEqualTo("novo@exemplo.com");
    }

    // ------------------------------------------------------------ Veículo

    @Test
    void criaVeiculoComPlacaNormalizada() {
        Veiculo v = Veiculo.novo("abc-1d23", "Chevrolet", "Onix", 2022, "52998224725");
        assertThat(v.placa().valor()).isEqualTo("ABC1D23");
        assertThat(v.ativo()).isTrue();
    }

    @Test
    void veiculoValidaCampos() {
        assertThatThrownBy(() -> Veiculo.novo("ABC1D23", " ", "Onix", 2022, "52998224725"))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> Veiculo.novo("ABC1D23", "Chevrolet", "Onix", 1800, "52998224725"))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Ano");
        assertThatThrownBy(() -> Veiculo.novo("ABC1D23", "Chevrolet", "Onix", 2300, "52998224725"))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Ano");
        assertThatThrownBy(() -> Veiculo.novo("ABC1D23", "Chevrolet", "Onix", 2022, null))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("cliente");
        assertThatThrownBy(() -> new Veiculo(null, null, "Chevrolet", "Onix", 2022, "52998224725", true))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Placa");
    }

    @Test
    void desativarEAtualizarVeiculo() {
        Veiculo v = Veiculo.novo("ABC1D23", "Chevrolet", "Onix", 2022, "52998224725");
        assertThat(v.desativar().ativo()).isFalse();
        Veiculo atualizado = v.atualizar("Fiat", "Argo", 2023);
        assertThat(atualizado.marca()).isEqualTo("Fiat");
        assertThat(atualizado.placa()).isEqualTo(v.placa());
    }

    // ------------------------------------------------------------ Serviço

    @Test
    void servicoArredondaPrecoParaDuasCasas() {
        Servico s = Servico.novo("Troca de óleo", null, new BigDecimal("120.5"), 30);
        assertThat(s.preco()).isEqualByComparingTo("120.50");
        assertThat(s.preco().scale()).isEqualTo(2);
        assertThat(s.ativo()).isTrue();
    }

    @Test
    void servicoValidaCampos() {
        assertThatThrownBy(() -> Servico.novo(" ", null, BigDecimal.TEN, 30))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Nome");
        assertThatThrownBy(() -> Servico.novo("X", null, BigDecimal.ZERO, 30))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Preço");
        assertThatThrownBy(() -> Servico.novo("X", null, null, 30))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Preço");
        assertThatThrownBy(() -> Servico.novo("X", null, BigDecimal.TEN, 0))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Tempo");
    }

    @Test
    void desativarEAtualizarServico() {
        Servico s = Servico.novo("Alinhamento", null, BigDecimal.TEN, 30);
        assertThat(s.desativar().ativo()).isFalse();
        Servico atualizado = s.atualizar("Alinhamento 3D", "completo", new BigDecimal("99.90"), 45);
        assertThat(atualizado.nome()).isEqualTo("Alinhamento 3D");
        assertThat(atualizado.tempoEstimadoMinutos()).isEqualTo(45);
    }

    // ------------------------------------------------------------ Peça

    @Test
    void pecaNormalizaCodigoEPreco() {
        Peca p = Peca.nova(" flt-01 ", "Filtro de óleo", null, new BigDecimal("35"));
        assertThat(p.codigo()).isEqualTo("FLT-01");
        assertThat(p.precoUnitario()).isEqualByComparingTo("35.00");
        assertThat(p.ativo()).isTrue();
    }

    @Test
    void pecaValidaCampos() {
        assertThatThrownBy(() -> Peca.nova(" ", "Filtro", null, BigDecimal.TEN))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Código");
        assertThatThrownBy(() -> Peca.nova("FLT", " ", null, BigDecimal.TEN))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Nome");
        assertThatThrownBy(() -> Peca.nova("FLT", "Filtro", null, BigDecimal.ZERO))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("Preço");
    }

    @Test
    void desativarEAtualizarPeca() {
        Peca p = Peca.nova("FLT", "Filtro", null, BigDecimal.TEN);
        assertThat(p.desativar().ativo()).isFalse();
        Peca atualizada = p.atualizar("Filtro premium", "melhor", new BigDecimal("50"));
        assertThat(atualizada.codigo()).isEqualTo("FLT");
        assertThat(atualizada.nome()).isEqualTo("Filtro premium");
    }
}
