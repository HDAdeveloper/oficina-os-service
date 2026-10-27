package br.com.oficina.os.infrastructure.web.dto;

import br.com.oficina.os.domain.model.Servico;

import java.math.BigDecimal;

public record ServicoResponse(Long id, String nome, String descricao, BigDecimal preco,
                              int tempoEstimadoMinutos, boolean ativo) {

    public static ServicoResponse de(Servico s) {
        return new ServicoResponse(s.id(), s.nome(), s.descricao(), s.preco(), s.tempoEstimadoMinutos(), s.ativo());
    }
}
