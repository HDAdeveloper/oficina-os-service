package br.com.oficina.os.domain.model;

import java.math.BigDecimal;

/** Peça dentro da OS, com o preço do momento em que foi adicionada. */
public record ItemPecaOS(Long pecaId, String codigo, String nome, int quantidade, BigDecimal precoUnitario) {

    public BigDecimal subtotal() {
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }
}
