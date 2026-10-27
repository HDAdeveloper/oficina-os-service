package br.com.oficina.os.infrastructure.web.dto;

public record TokenResponse(String token, String tipo, long expiraEmSegundos) {
}
