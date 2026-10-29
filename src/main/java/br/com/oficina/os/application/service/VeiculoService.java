package br.com.oficina.os.application.service;

import br.com.oficina.os.application.port.in.GestaoVeiculos;
import br.com.oficina.os.application.port.out.ClienteRepository;
import br.com.oficina.os.application.port.out.VeiculoRepository;
import br.com.oficina.os.domain.exception.ConflitoException;
import br.com.oficina.os.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.os.domain.exception.RegraDeNegocioException;
import br.com.oficina.os.domain.model.Cliente;
import br.com.oficina.os.domain.model.Documento;
import br.com.oficina.os.domain.model.Placa;
import br.com.oficina.os.domain.model.Veiculo;

import java.util.List;

public class VeiculoService implements GestaoVeiculos {

    private final VeiculoRepository veiculos;
    private final ClienteRepository clientes;

    public VeiculoService(VeiculoRepository veiculos, ClienteRepository clientes) {
        this.veiculos = veiculos;
        this.clientes = clientes;
    }

    @Override
    public Veiculo cadastrar(NovoVeiculo dados) {
        if (dados.clienteId() == null || dados.clienteId().isBlank()) {
            throw new RegraDeNegocioException("Veículo precisa pertencer a um cliente");
        }
        Cliente dono = clientes.buscarPorId(Documento.normalizar(dados.clienteId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Cliente não encontrado: " + dados.clienteId()));
        if (!dono.ativo()) {
            throw new RegraDeNegocioException("Cliente inativo: " + dono.nome());
        }
        Veiculo novo = Veiculo.novo(dados.placa(), dados.marca(), dados.modelo(), dados.ano(), dono.id());
        if (veiculos.buscarPorPlaca(novo.placa().valor()).isPresent()) {
            throw new ConflitoException("Já existe veículo com a placa " + novo.placa().valor());
        }
        return veiculos.salvar(novo);
    }

    @Override
    public Veiculo atualizar(Long id, AlteracaoVeiculo dados) {
        return veiculos.salvar(buscarPorId(id).atualizar(dados.marca(), dados.modelo(), dados.ano()));
    }

    @Override
    public void desativar(Long id) {
        veiculos.salvar(buscarPorId(id).desativar());
    }

    @Override
    public Veiculo buscarPorId(Long id) {
        return veiculos.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo não encontrado: " + id));
    }

    @Override
    public Veiculo buscarPorPlaca(String placa) {
        return veiculos.buscarPorPlaca(Placa.normalizar(placa))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo não encontrado com a placa: " + placa));
    }

    @Override
    public List<Veiculo> listarPorCliente(String clienteId) {
        return veiculos.listarPorCliente(Documento.normalizar(clienteId));
    }

    @Override
    public List<Veiculo> listar() {
        return veiculos.listarAtivos();
    }
}
