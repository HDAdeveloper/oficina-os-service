package br.com.oficina.os.domain.model;

import br.com.oficina.os.domain.exception.RegraDeNegocioException;

import java.time.Year;

public record Veiculo(Long id, Placa placa, String marca, String modelo, int ano,
                      String clienteId, boolean ativo) {

    public Veiculo {
        if (placa == null) {
            throw new RegraDeNegocioException("Placa é obrigatória");
        }
        if (marca == null || marca.isBlank() || modelo == null || modelo.isBlank()) {
            throw new RegraDeNegocioException("Marca e modelo do veículo são obrigatórios");
        }
        int anoMaximo = Year.now().getValue() + 1;
        if (ano < 1900 || ano > anoMaximo) {
            throw new RegraDeNegocioException("Ano do veículo deve estar entre 1900 e " + anoMaximo);
        }
        if (clienteId == null || clienteId.isBlank()) {
            throw new RegraDeNegocioException("Veículo precisa pertencer a um cliente");
        }
    }

    public static Veiculo novo(String placa, String marca, String modelo, int ano, String clienteId) {
        return new Veiculo(null, Placa.of(placa), marca, modelo, ano, clienteId, true);
    }

    public Veiculo desativar() {
        return new Veiculo(id, placa, marca, modelo, ano, clienteId, false);
    }

    public Veiculo atualizar(String novaMarca, String novoModelo, int novoAno) {
        return new Veiculo(id, placa, novaMarca, novoModelo, novoAno, clienteId, ativo);
    }
}
