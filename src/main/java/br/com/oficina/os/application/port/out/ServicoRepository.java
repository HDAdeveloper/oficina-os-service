package br.com.oficina.os.application.port.out;

import br.com.oficina.os.domain.model.Servico;

import java.util.List;
import java.util.Optional;

/** Porta de saída: persistência do catálogo de serviços. */
public interface ServicoRepository {

    Servico salvar(Servico servico);

    Optional<Servico> buscarPorId(Long id);

    List<Servico> listarAtivos();
}
