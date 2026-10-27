package br.com.oficina.os.application.service;

import br.com.oficina.os.application.port.in.GestaoClientes;
import br.com.oficina.os.application.port.out.ClienteRepository;
import br.com.oficina.os.domain.exception.ConflitoException;
import br.com.oficina.os.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.os.domain.model.Cliente;
import br.com.oficina.os.domain.model.Documento;

import java.util.List;

public class ClienteService implements GestaoClientes {

    private final ClienteRepository clientes;

    public ClienteService(ClienteRepository clientes) {
        this.clientes = clientes;
    }

    @Override
    public Cliente cadastrar(NovoCliente dados) {
        Cliente novo = Cliente.novo(dados.nome(), dados.documento(), dados.email(),
                dados.telefone(), dados.endereco());
        if (clientes.buscarPorDocumento(novo.documento().numero()).isPresent()) {
            throw new ConflitoException("Já existe cliente com o documento " + novo.documento().formatado());
        }
        return clientes.salvar(novo);
    }

    @Override
    public Cliente atualizar(Long id, AlteracaoCliente dados) {
        Cliente atual = buscarPorId(id);
        return clientes.salvar(atual.atualizar(dados.nome(), dados.email(), dados.telefone(), dados.endereco()));
    }

    @Override
    public void desativar(Long id) {
        clientes.salvar(buscarPorId(id).desativar());
    }

    @Override
    public Cliente buscarPorId(Long id) {
        return clientes.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado: " + id));
    }

    @Override
    public Cliente buscarPorDocumento(String documento) {
        return clientes.buscarPorDocumento(Documento.normalizar(documento))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Cliente não encontrado com o documento: " + documento));
    }

    @Override
    public List<Cliente> listar() {
        return clientes.listarAtivos();
    }
}
