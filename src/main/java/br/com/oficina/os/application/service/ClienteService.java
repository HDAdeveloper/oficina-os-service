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
        var existente = clientes.buscarPorId(novo.id());
        if (existente.isPresent()) {
            String dica = existente.get().ativo() ? "" : " (está inativo: use a reativação)";
            throw new ConflitoException("Já existe cliente com o documento " + novo.documento().formatado() + dica);
        }
        return clientes.salvar(novo);
    }

    @Override
    public Cliente atualizar(String id, AlteracaoCliente dados) {
        Cliente atual = buscarPorId(id);
        return clientes.salvar(atual.atualizar(dados.nome(), dados.email(), dados.telefone(), dados.endereco()));
    }

    @Override
    public void desativar(String id) {
        clientes.salvar(buscarPorId(id).desativar());
    }

    @Override
    public Cliente reativar(String id) {
        return clientes.salvar(buscarPorId(id).reativar());
    }

    @Override
    public Cliente buscarPorId(String id) {
        return clientes.buscarPorId(Documento.normalizar(id))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado: " + id));
    }

    @Override
    public Cliente buscarPorDocumento(String documento) {
        return clientes.buscarPorId(Documento.normalizar(documento))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Cliente não encontrado com o documento: " + documento));
    }

    @Override
    public List<Cliente> listar() {
        return clientes.listarAtivos();
    }
}
