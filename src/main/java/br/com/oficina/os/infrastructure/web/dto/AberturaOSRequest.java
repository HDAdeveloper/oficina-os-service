package br.com.oficina.os.infrastructure.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AberturaOSRequest(
        @NotBlank @Size(max = 18) String documentoCliente,
        @NotBlank @Size(max = 8) String placaVeiculo,
        @NotBlank @Size(max = 1000) String descricaoProblema,
        @Size(max = 1000) String observacoes,
        @Valid List<ItemOSRequest> servicos,
        @Valid List<ItemOSRequest> pecas) {
}
