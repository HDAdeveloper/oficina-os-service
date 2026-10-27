package br.com.oficina.os.infrastructure.persistence;

import br.com.oficina.os.application.port.out.ServicoRepository;
import br.com.oficina.os.domain.model.Servico;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ServicoRepositoryAdapter implements ServicoRepository {

    private final ServicoJpa jpa;

    public ServicoRepositoryAdapter(ServicoJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Servico salvar(Servico servico) {
        ServicoEntity e = servico.id() == null
                ? new ServicoEntity()
                : jpa.findById(servico.id()).orElseGet(ServicoEntity::new);
        PersistenciaMapper.aplicar(e, servico);
        return PersistenciaMapper.paraDominio(jpa.saveAndFlush(e));
    }

    @Override
    public Optional<Servico> buscarPorId(Long id) {
        return jpa.findById(id).map(PersistenciaMapper::paraDominio);
    }

    @Override
    public List<Servico> listarAtivos() {
        return jpa.findByAtivoTrueOrderByNome().stream().map(PersistenciaMapper::paraDominio).toList();
    }
}
