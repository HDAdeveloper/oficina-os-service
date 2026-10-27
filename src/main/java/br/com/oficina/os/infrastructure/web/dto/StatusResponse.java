package br.com.oficina.os.infrastructure.web.dto;

import br.com.oficina.os.domain.model.StatusOS;

public record StatusResponse(String status, String descricao) {

    public static StatusResponse de(StatusOS s) {
        return new StatusResponse(s.name(), s.getDescricao());
    }
}
