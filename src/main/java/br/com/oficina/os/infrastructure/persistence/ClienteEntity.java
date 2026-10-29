package br.com.oficina.os.infrastructure.persistence;

import br.com.oficina.os.domain.model.TipoDocumento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Modelo de persistência: fica só na infraestrutura, o domínio não conhece JPA. */
@Entity
@Table(name = "cliente")
public class ClienteEntity {

    /** O CPF/CNPJ (só dígitos) é a chave primária. */
    @Id
    @Column(nullable = false, length = 14)
    String documento;

    @Column(nullable = false)
    String nome;

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
