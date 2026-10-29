package br.com.oficina.os.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record VeiculoRequest(
        @NotBlank @Size(max = 8) String placa,
        @NotBlank @Size(max = 80) String marca,
        @NotBlank @Size(max = 80) String modelo,
        @Positive int ano,
        @NotBlank @Size(max = 18) String clienteId) {
}
