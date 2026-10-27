package br.com.oficina.os.application.port.in;

import br.com.oficina.os.domain.model.Servico;

import java.math.BigDecimal;
import java.util.List;

/** Porta de entrada: catálogo de serviços. */
public interface GestaoServicos {

    record NovoServico(String nome, String descricao, BigDecimal preco, int tempoEstimadoMinutos) {
    }

    record AlteracaoServico(String nome, String descricao, BigDecimal preco, int tempoEstimadoMinutos) {
    }

    Servico cadastrar(NovoServico dados);

    Servico atualizar(Long id, AlteracaoServico dados);

    void desativar(Long id);

    Servico buscarPorId(Long id);

    List<Servico> listar();
}
