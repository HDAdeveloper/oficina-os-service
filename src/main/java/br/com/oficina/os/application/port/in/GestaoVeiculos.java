package br.com.oficina.os.application.port.in;

import br.com.oficina.os.domain.model.Veiculo;

import java.util.List;

/** Porta de entrada: casos de uso de veículos. */
public interface GestaoVeiculos {

    record NovoVeiculo(String placa, String marca, String modelo, int ano, Long clienteId) {
    }

    record AlteracaoVeiculo(String marca, String modelo, int ano) {
    }

    Veiculo cadastrar(NovoVeiculo dados);

    Veiculo atualizar(Long id, AlteracaoVeiculo dados);

    void desativar(Long id);

    Veiculo buscarPorId(Long id);

    Veiculo buscarPorPlaca(String placa);

    List<Veiculo> listarPorCliente(Long clienteId);

    List<Veiculo> listar();
}
