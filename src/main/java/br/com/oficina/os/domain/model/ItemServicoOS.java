package br.com.oficina.os.domain.model;

import java.math.BigDecimal;

/** Serviço dentro da OS, com o preço do momento em que foi adicionado. */
public record ItemServicoOS(Long servicoId, String nome, int quantidade, BigDecimal precoUnitario) {

    public BigDecimal subtotal() {
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }
}
