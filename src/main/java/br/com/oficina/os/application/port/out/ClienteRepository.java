package br.com.oficina.os.application.port.out;

import br.com.oficina.os.domain.model.Cliente;

import java.util.List;
import java.util.Optional;

/** Porta de saída: persistência de clientes. */
public interface ClienteRepository {

    /** Insere ou atualiza (a chave é o documento). */
    Cliente salvar(Cliente cliente);

    /** O id do cliente é o documento (CPF/CNPJ) só com dígitos. */
    Optional<Cliente> buscarPorId(String id);

    List<Cliente> listarAtivos();
}
