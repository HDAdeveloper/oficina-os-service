package br.com.oficina.os.infrastructure.web.dto;

import br.com.oficina.os.domain.model.Cliente;

public record ClienteResponse(Long id, String nome, String documento, String tipoDocumento, String email,
                              String telefone, String endereco, boolean ativo) {

    public static ClienteResponse de(Cliente c) {
        return new ClienteResponse(c.id(), c.nome(), c.documento().formatado(), c.documento().tipo().name(),
                c.email(), c.telefone(), c.endereco(), c.ativo());
    }
}
