package br.com.oficina.os.application.port.out;

import br.com.oficina.os.domain.model.OrdemServico;
import br.com.oficina.os.domain.model.StatusOS;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Porta de saída: persistência das ordens de serviço. */
public interface OrdemServicoRepository {

    /** Insere (id nulo) ou atualiza; devolve a OS com o id preenchido. */
    OrdemServico salvar(OrdemServico os);

    Optional<OrdemServico> buscarPorId(Long id);

    List<OrdemServico> buscarPorStatus(Collection<StatusOS> status);

    /** OS do cliente, a mais recente primeiro. */
    List<OrdemServico> buscarPorCliente(String clienteId);
}
