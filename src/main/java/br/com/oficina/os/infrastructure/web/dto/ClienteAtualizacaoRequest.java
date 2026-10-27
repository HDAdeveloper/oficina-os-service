package br.com.oficina.os.infrastructure.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClienteAtualizacaoRequest(
        @NotBlank @Size(max = 150) String nome,
        @Email @Size(max = 150) String email,
        @Size(max = 30) String telefone,
        @Size(max = 255) String endereco) {
}
