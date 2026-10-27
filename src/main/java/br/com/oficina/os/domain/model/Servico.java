package br.com.oficina.os.domain.model;

import br.com.oficina.os.domain.exception.RegraDeNegocioException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Serviço do catálogo (ex.: troca de óleo). O preço aqui é o valor atual, copiado para a OS ao adicionar. */
public record Servico(Long id, String nome, String descricao, BigDecimal preco,
                      int tempoEstimadoMinutos, boolean ativo) {

    public Servico {
        if (nome == null || nome.isBlank()) {
            throw new RegraDeNegocioException("Nome do serviço é obrigatório");
        }
        if (preco == null || preco.signum() <= 0) {
            throw new RegraDeNegocioException("Preço do serviço deve ser maior que zero");
        }
        if (tempoEstimadoMinutos <= 0) {
            throw new RegraDeNegocioException("Tempo estimado deve ser maior que zero");
        }
        preco = preco.setScale(2, RoundingMode.HALF_UP);
    }

    public static Servico novo(String nome, String descricao, BigDecimal preco, int tempoEstimadoMinutos) {
        return new Servico(null, nome, descricao, preco, tempoEstimadoMinutos, true);
    }

    public Servico desativar() {
        return new Servico(id, nome, descricao, preco, tempoEstimadoMinutos, false);
    }

    public Servico atualizar(String novoNome, String novaDescricao, BigDecimal novoPreco, int novoTempo) {
        return new Servico(id, novoNome, novaDescricao, novoPreco, novoTempo, ativo);
    }
}
