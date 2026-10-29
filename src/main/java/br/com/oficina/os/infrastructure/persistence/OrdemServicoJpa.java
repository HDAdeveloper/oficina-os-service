package br.com.oficina.os.infrastructure.persistence;

import br.com.oficina.os.domain.model.StatusOS;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OrdemServicoJpa extends JpaRepository<OrdemServicoEntity, Long> {
    List<OrdemServicoEntity> findByStatusIn(Collection<StatusOS> status);

    List<OrdemServicoEntity> findByClienteIdOrderByDataAberturaDesc(String clienteId);
}
