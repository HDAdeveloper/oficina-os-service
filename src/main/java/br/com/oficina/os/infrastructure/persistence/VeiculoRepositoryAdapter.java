package br.com.oficina.os.infrastructure.persistence;

import br.com.oficina.os.application.port.out.VeiculoRepository;
import br.com.oficina.os.domain.model.Veiculo;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class VeiculoRepositoryAdapter implements VeiculoRepository {

    private final VeiculoJpa jpa;

    public VeiculoRepositoryAdapter(VeiculoJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Veiculo salvar(Veiculo veiculo) {
        VeiculoEntity e = veiculo.id() == null
                ? new VeiculoEntity()
                : jpa.findById(veiculo.id()).orElseGet(VeiculoEntity::new);
        PersistenciaMapper.aplicar(e, veiculo);
        return PersistenciaMapper.paraDominio(jpa.saveAndFlush(e));
    }

    @Override
    public Optional<Veiculo> buscarPorId(Long id) {
        return jpa.findById(id).map(PersistenciaMapper::paraDominio);
    }

    @Override
    public Optional<Veiculo> buscarPorPlaca(String placa) {
        return jpa.findByPlaca(placa).map(PersistenciaMapper::paraDominio);
    }

    @Override
    public List<Veiculo> listarPorCliente(String clienteId) {
        return jpa.findByClienteIdOrderByPlaca(clienteId).stream().map(PersistenciaMapper::paraDominio).toList();
    }

    @Override
    public List<Veiculo> listarAtivos() {
        return jpa.findByAtivoTrueOrderByPlaca().stream().map(PersistenciaMapper::paraDominio).toList();
    }
}
