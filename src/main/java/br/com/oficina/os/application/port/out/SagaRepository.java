package br.com.oficina.os.application.port.out;

import br.com.oficina.os.domain.model.Saga;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Porta de saída: persistência das instâncias da Saga. */
public interface SagaRepository {

    Saga salvar(Saga saga);

    Optional<Saga> buscarPorId(String id);

    /** Todas as Sagas da OS, da mais recente para a mais antiga. */
    List<Saga> buscarPorOs(Long osId);

    /** As Sagas mexidas há menos tempo, da mais recente para a mais antiga (painel de acompanhamento). */
    List<Saga> buscarRecentes(int limite);

    /** Sagas que aguardam resposta e cujo prazo já passou. */
    List<Saga> buscarComPrazoVencido(Instant agora);
}
