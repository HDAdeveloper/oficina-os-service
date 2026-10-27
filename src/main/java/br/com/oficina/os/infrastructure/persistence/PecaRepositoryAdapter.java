package br.com.oficina.os.infrastructure.persistence;

import br.com.oficina.os.application.port.out.PecaRepository;
import br.com.oficina.os.domain.model.Peca;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PecaRepositoryAdapter implements PecaRepository {

    private final PecaJpa jpa;

    public PecaRepositoryAdapter(PecaJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Peca salvar(Peca peca) {
        PecaEntity e = peca.id() == null
                ? new PecaEntity()
                : jpa.findById(peca.id()).orElseGet(PecaEntity::new);
        PersistenciaMapper.aplicar(e, peca);
        return PersistenciaMapper.paraDominio(jpa.saveAndFlush(e));
    }

    @Override
    public Optional<Peca> buscarPorId(Long id) {
        return jpa.findById(id).map(PersistenciaMapper::paraDominio);
    }

    @Override
    public Optional<Peca> buscarPorCodigo(String codigo) {
        return jpa.findByCodigo(codigo).map(PersistenciaMapper::paraDominio);
    }

    @Override
    public List<Peca> listarAtivas() {
        return jpa.findByAtivoTrueOrderByNome().stream().map(PersistenciaMapper::paraDominio).toList();
    }
}
