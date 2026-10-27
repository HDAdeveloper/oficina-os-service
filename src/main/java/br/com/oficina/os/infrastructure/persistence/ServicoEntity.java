package br.com.oficina.os.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "servico")
public class ServicoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false)
    String nome;

    String descricao;

    @Column(nullable = false, precision = 12, scale = 2)
    BigDecimal preco;

    @Column(name = "tempo_estimado_minutos", nullable = false)
    int tempoEstimadoMinutos;

    @Column(nullable = false)
    boolean ativo;

    protected ServicoEntity() {
    }
}
