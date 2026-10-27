package br.com.oficina.os.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

@Embeddable
class ItemPecaEmb {

    @Column(name = "peca_id", nullable = false)
    Long pecaId;

    @Column(nullable = false)
    String codigo;

    @Column(nullable = false)
    String nome;

    @Column(nullable = false)
    int quantidade;

    @Column(name = "preco_unitario", nullable = false, precision = 12, scale = 2)
    BigDecimal precoUnitario;

    protected ItemPecaEmb() {
    }

    ItemPecaEmb(Long pecaId, String codigo, String nome, int quantidade, BigDecimal precoUnitario) {
        this.pecaId = pecaId;
        this.codigo = codigo;
        this.nome = nome;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario;
    }
}
