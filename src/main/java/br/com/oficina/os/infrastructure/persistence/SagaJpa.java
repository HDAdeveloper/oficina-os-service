package br.com.oficina.os.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SagaJpa extends JpaRepository<SagaEntity, String> {

    List<SagaEntity> findByOsIdOrderByCriadaEmDesc(Long osId);

    List<SagaEntity> findAllByOrderByAtualizadaEmDesc(Pageable pagina);

    List<SagaEntity> findByPrazoLessThanEqual(Instant limite);
}
