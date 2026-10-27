package br.com.oficina.os.infrastructure.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Corpo opcional da aprovação. No provedor de pagamento simulado, {@code metodoPagamento} comanda o resultado
 * (APRO aprova; FUND e OTHE recusam; ERRO simula provedor fora do ar; ERRO_ESTORNO falha no estorno).
 */
public record AprovacaoRequest(@Size(max = 40) String metodoPagamento) {
}
