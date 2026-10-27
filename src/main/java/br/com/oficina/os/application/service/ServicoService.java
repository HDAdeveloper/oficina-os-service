package br.com.oficina.os.application.service;

import br.com.oficina.os.application.port.in.GestaoServicos;
import br.com.oficina.os.application.port.out.ServicoRepository;
import br.com.oficina.os.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.os.domain.model.Servico;

import java.util.List;

public class ServicoService implements GestaoServicos {

    private final ServicoRepository servicos;

    public ServicoService(ServicoRepository servicos) {
        this.servicos = servicos;
    }

    @Override
    public Servico cadastrar(NovoServico dados) {
        return servicos.salvar(Servico.novo(dados.nome(), dados.descricao(), dados.preco(),
                dados.tempoEstimadoMinutos()));
    }

    @Override
    public Servico atualizar(Long id, AlteracaoServico dados) {
        return servicos.salvar(buscarPorId(id).atualizar(dados.nome(), dados.descricao(), dados.preco(),
                dados.tempoEstimadoMinutos()));
    }

    @Override
    public void desativar(Long id) {
        servicos.salvar(buscarPorId(id).desativar());
    }

    @Override
    public Servico buscarPorId(Long id) {
        return servicos.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado: " + id));
    }

    @Override
    public List<Servico> listar() {
        return servicos.listarAtivos();
    }
}
