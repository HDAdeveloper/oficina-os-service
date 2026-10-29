package br.com.oficina.os.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VeiculoJpa extends JpaRepository<VeiculoEntity, Long> {
    Optional<VeiculoEntity> findByPlaca(String placa);

    List<VeiculoEntity> findByClienteIdOrderByPlaca(String clienteId);

    List<VeiculoEntity> findByAtivoTrueOrderByPlaca();
}
