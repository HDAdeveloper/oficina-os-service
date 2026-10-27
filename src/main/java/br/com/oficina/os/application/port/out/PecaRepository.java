package br.com.oficina.os.application.port.out;

import br.com.oficina.os.domain.model.Peca;

import java.util.List;
import java.util.Optional;

/** Porta de saída: persistência do catálogo de peças. */
public interface PecaRepository {

    Peca salvar(Peca peca);

    Optional<Peca> buscarPorId(Long id);

    /** Código em maiúsculas. */
    Optional<Peca> buscarPorCodigo(String codigo);

    List<Peca> listarAtivas();
}
