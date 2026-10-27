package br.com.oficina.os.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "peca")
public class PecaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false)
    String codigo;

    @Column(nullable = false)
    String nome;

    String descricao;

    @Column(name = "preco_unitario", nullable = false, precision = 12, scale = 2)
    BigDecimal precoUnitario;

    @Column(nullable = false)
    boolean ativo;

    protected PecaEntity() {
    }
}
