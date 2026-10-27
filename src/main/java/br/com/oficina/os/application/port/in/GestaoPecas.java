package br.com.oficina.os.application.port.in;

import br.com.oficina.os.domain.model.Peca;

import java.math.BigDecimal;
import java.util.List;

/** Porta de entrada: catálogo de peças (nome e preço; o estoque é do Execution Service). */
public interface GestaoPecas {

    record NovaPeca(String codigo, String nome, String descricao, BigDecimal precoUnitario) {
    }

    record AlteracaoPeca(String nome, String descricao, BigDecimal precoUnitario) {
    }

    Peca cadastrar(NovaPeca dados);

    Peca atualizar(Long id, AlteracaoPeca dados);

    void desativar(Long id);

    Peca buscarPorId(Long id);

    Peca buscarPorCodigo(String codigo);

    List<Peca> listar();
}
