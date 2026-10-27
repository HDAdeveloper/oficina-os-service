package br.com.oficina.os.infrastructure.persistence;

import br.com.oficina.os.domain.model.TipoDocumento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Modelo de persistência: fica só na infraestrutura, o domínio não conhece JPA. */
@Entity
@Table(name = "cliente")
public class ClienteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false)
    String nome;

    @Column(nullable = false)
    String documento;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false)
    TipoDocumento tipoDocumento;

    String email;
    String telefone;
    String endereco;

    @Column(nullable = false)
    boolean ativo;

    protected ClienteEntity() {
    }
}
