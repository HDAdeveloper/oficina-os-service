package br.com.oficina.os.infrastructure.web.dto;

import br.com.oficina.os.domain.model.Peca;

import java.math.BigDecimal;

public record PecaResponse(Long id, String codigo, String nome, String descricao, BigDecimal precoUnitario,
                           boolean ativo) {

    public static PecaResponse de(Peca p) {
        return new PecaResponse(p.id(), p.codigo(), p.nome(), p.descricao(), p.precoUnitario(), p.ativo());
    }
}
