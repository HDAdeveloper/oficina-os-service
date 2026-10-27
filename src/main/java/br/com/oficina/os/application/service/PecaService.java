package br.com.oficina.os.application.service;

import br.com.oficina.os.application.port.in.GestaoPecas;
import br.com.oficina.os.application.port.out.PecaRepository;
import br.com.oficina.os.domain.exception.ConflitoException;
import br.com.oficina.os.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.os.domain.model.Peca;

import java.util.List;

public class PecaService implements GestaoPecas {

    private final PecaRepository pecas;

    public PecaService(PecaRepository pecas) {
        this.pecas = pecas;
    }

    @Override
    public Peca cadastrar(NovaPeca dados) {
        Peca nova = Peca.nova(dados.codigo(), dados.nome(), dados.descricao(), dados.precoUnitario());
        if (pecas.buscarPorCodigo(nova.codigo()).isPresent()) {
            throw new ConflitoException("Já existe peça com o código " + nova.codigo());
        }
        return pecas.salvar(nova);
    }

    @Override
    public Peca atualizar(Long id, AlteracaoPeca dados) {
        return pecas.salvar(buscarPorId(id).atualizar(dados.nome(), dados.descricao(), dados.precoUnitario()));
    }

    @Override
    public void desativar(Long id) {
        pecas.salvar(buscarPorId(id).desativar());
    }

    @Override
    public Peca buscarPorId(Long id) {
        return pecas.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Peça não encontrada: " + id));
    }

    @Override
    public Peca buscarPorCodigo(String codigo) {
        String normalizado = codigo == null ? null : codigo.trim().toUpperCase();
        return pecas.buscarPorCodigo(normalizado)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Peça não encontrada com o código: " + codigo));
    }

    @Override
    public List<Peca> listar() {
        return pecas.listarAtivas();
    }
}
