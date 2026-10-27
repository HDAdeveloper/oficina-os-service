package br.com.oficina.os.infrastructure.web.dto;

import jakarta.validation.constraints.Size;

public record MotivoRequest(@Size(max = 500) String motivo) {
}
