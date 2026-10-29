package br.com.oficina.os.infrastructure.web.dto;

import br.com.oficina.os.domain.model.Veiculo;

public record VeiculoResponse(Long id, String placa, String marca, String modelo, int ano, String clienteId,
                              boolean ativo) {

    public static VeiculoResponse de(Veiculo v) {
        return new VeiculoResponse(v.id(), v.placa().valor(), v.marca(), v.modelo(), v.ano(), v.clienteId(),
                v.ativo());
    }
}
