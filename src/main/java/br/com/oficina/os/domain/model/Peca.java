package br.com.oficina.os.domain.model;

import br.com.oficina.os.domain.exception.RegraDeNegocioException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Peça do catálogo. Aqui ficam só nome e preço; a QUANTIDADE EM ESTOQUE pertence ao
 * Execution Service (ADR-001). O {@code codigo} é a chave que liga os dois serviços.
 */
public record Peca(Long id, String codigo, String nome, String descricao,
                   BigDecimal precoUnitario, boolean ativo) {

    public Peca {
        if (codigo == null || codigo.isBlank()) {
            throw new RegraDeNegocioException("Código da peça é obrigatório");
        }
        if (nome == null || nome.isBlank()) {
            throw new RegraDeNegocioException("Nome da peça é obrigatório");
        }
        if (precoUnitario == null || precoUnitario.signum() <= 0) {
            throw new RegraDeNegocioException("Preço da peça deve ser maior que zero");
        }
        codigo = codigo.trim().toUpperCase();
        precoUnitario = precoUnitario.setScale(2, RoundingMode.HALF_UP);
    }

    public static Peca nova(String codigo, String nome, String descricao, BigDecimal precoUnitario) {
        return new Peca(null, codigo, nome, descricao, precoUnitario, true);
    }

    public Peca desativar() {
        return new Peca(id, codigo, nome, descricao, precoUnitario, false);
    }

    public Peca atualizar(String novoNome, String novaDescricao, BigDecimal novoPreco) {
        return new Peca(id, codigo, novoNome, novaDescricao, novoPreco, ativo);
    }
}
