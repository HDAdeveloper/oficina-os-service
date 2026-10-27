package br.com.oficina.os.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Serviço ou peça do catálogo (pelo id) e a quantidade. */
public record ItemOSRequest(@NotNull Long itemId, @Positive int quantidade) {
}
