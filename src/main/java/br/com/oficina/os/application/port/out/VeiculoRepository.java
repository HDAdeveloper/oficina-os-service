package br.com.oficina.os.application.port.out;

import br.com.oficina.os.domain.model.Veiculo;

import java.util.List;
import java.util.Optional;

/** Porta de saída: persistência de veículos. */
public interface VeiculoRepository {

    Veiculo salvar(Veiculo veiculo);

    Optional<Veiculo> buscarPorId(Long id);

    /** Placa normalizada (maiúsculas, sem hífen). */
    Optional<Veiculo> buscarPorPlaca(String placa);

    List<Veiculo> listarPorCliente(Long clienteId);

    List<Veiculo> listarAtivos();
}
