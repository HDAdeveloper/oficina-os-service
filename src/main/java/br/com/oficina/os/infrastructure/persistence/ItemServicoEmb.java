package br.com.oficina.os.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

@Embeddable
class ItemServicoEmb {

    @Column(name = "servico_id", nullable = false)
    Long servicoId;

    @Column(nullable = false)
    String nome;

    @Column(nullable = false)
    int quantidade;

    @Column(name = "preco_unitario", nullable = false, precision = 12, scale = 2)
    BigDecimal precoUnitario;

    protected ItemServicoEmb() {
    }

    ItemServicoEmb(Long servicoId, String nome, int quantidade, BigDecimal precoUnitario) {
        this.servicoId = servicoId;
        this.nome = nome;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario;
    }
}
