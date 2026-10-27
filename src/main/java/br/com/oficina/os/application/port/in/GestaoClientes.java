package br.com.oficina.os.application.port.in;

import br.com.oficina.os.domain.model.Cliente;

import java.util.List;

/** Porta de entrada: casos de uso de clientes. */
public interface GestaoClientes {

    record NovoCliente(String nome, String documento, String email, String telefone, String endereco) {
    }

    record AlteracaoCliente(String nome, String email, String telefone, String endereco) {
    }

    Cliente cadastrar(NovoCliente dados);

    Cliente atualizar(Long id, AlteracaoCliente dados);

    void desativar(Long id);

    Cliente buscarPorId(Long id);

    /** Aceita o documento com ou sem máscara. */
    Cliente buscarPorDocumento(String documento);

    List<Cliente> listar();
}
