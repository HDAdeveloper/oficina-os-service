package br.com.oficina.os.infrastructure.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Usado no cadastro e na atualização de serviços do catálogo. */
public record ServicoRequest(
        @NotBlank @Size(max = 150) String nome,
        @Size(max = 500) String descricao,
        @NotNull @Digits(integer = 10, fraction = 2) BigDecimal preco,
        @Positive int tempoEstimadoMinutos) {
}
