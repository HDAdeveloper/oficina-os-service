package br.com.oficina.os.application.port.out;

import br.com.oficina.os.domain.model.Cliente;

import java.util.List;
import java.util.Optional;

/** Porta de saída: persistência de clientes. */
public interface ClienteRepository {

    /** Insere (id nulo) ou atualiza; devolve o cliente com o id preenchido. */
    Cliente salvar(Cliente cliente);

    Optional<Cliente> buscarPorId(Long id);

    /** Documento só com dígitos. */
    Optional<Cliente> buscarPorDocumento(String documento);

    List<Cliente> listarAtivos();
}
