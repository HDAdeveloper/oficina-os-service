package br.com.oficina.os.infrastructure.persistence;

import br.com.oficina.os.domain.model.Cliente;
import br.com.oficina.os.domain.model.Documento;
import br.com.oficina.os.domain.model.HistoricoStatus;
import br.com.oficina.os.domain.model.ItemPecaOS;
import br.com.oficina.os.domain.model.ItemServicoOS;
import br.com.oficina.os.domain.model.OrdemServico;
import br.com.oficina.os.domain.model.Peca;
import br.com.oficina.os.domain.model.Placa;
import br.com.oficina.os.domain.model.Servico;
import br.com.oficina.os.domain.model.Veiculo;

/** Converte entre o domínio (sem JPA) e as entidades de persistência. */
final class PersistenciaMapper {

    private PersistenciaMapper() {
    }

    // ------------------------------------------------------------ cliente

    static Cliente paraDominio(ClienteEntity e) {
        return new Cliente(e.id, e.nome, Documento.of(e.documento), e.email, e.telefone, e.endereco, e.ativo);
    }

    static void aplicar(ClienteEntity e, Cliente c) {
        e.nome = c.nome();
        e.documento = c.documento().numero();
        e.tipoDocumento = c.documento().tipo();
        e.email = c.email();
        e.telefone = c.telefone();
        e.endereco = c.endereco();
        e.ativo = c.ativo();
    }

    // ------------------------------------------------------------ veículo

    static Veiculo paraDominio(VeiculoEntity e) {
        return new Veiculo(e.id, Placa.of(e.placa), e.marca, e.modelo, e.ano, e.clienteId, e.ativo);
    }

    static void aplicar(VeiculoEntity e, Veiculo v) {
        e.placa = v.placa().valor();
        e.marca = v.marca();
        e.modelo = v.modelo();
        e.ano = v.ano();
        e.clienteId = v.clienteId();
        e.ativo = v.ativo();
    }

    // ------------------------------------------------------------ serviço

    static Servico paraDominio(ServicoEntity e) {
        return new Servico(e.id, e.nome, e.descricao, e.preco, e.tempoEstimadoMinutos, e.ativo);
    }

    static void aplicar(ServicoEntity e, Servico s) {
        e.nome = s.nome();
        e.descricao = s.descricao();
        e.preco = s.preco();
        e.tempoEstimadoMinutos = s.tempoEstimadoMinutos();
        e.ativo = s.ativo();
    }

    // ------------------------------------------------------------ peça

    static Peca paraDominio(PecaEntity e) {
        return new Peca(e.id, e.codigo, e.nome, e.descricao, e.precoUnitario, e.ativo);
    }

    static void aplicar(PecaEntity e, Peca p) {
        e.codigo = p.codigo();
        e.nome = p.nome();
        e.descricao = p.descricao();
        e.precoUnitario = p.precoUnitario();
        e.ativo = p.ativo();
    }

    // ------------------------------------------------------------ ordem de serviço

    static OrdemServico paraDominio(OrdemServicoEntity e) {
        return OrdemServico.reconstituir(new OrdemServico.Dados(
                e.id, e.numero, e.clienteId, e.veiculoId, e.status, e.descricaoProblema, e.observacoes,
                e.orcamentoId, e.motivoCancelamento,
                e.itensServico.stream()
                        .map(i -> new ItemServicoOS(i.servicoId, i.nome, i.quantidade, i.precoUnitario)).toList(),
                e.itensPeca.stream()
                        .map(i -> new ItemPecaOS(i.pecaId, i.codigo, i.nome, i.quantidade, i.precoUnitario)).toList(),
                e.historico.stream()
                        .map(h -> new HistoricoStatus(h.de, h.para, h.em, h.motivo)).toList(),
                e.dataAbertura, e.dataInicio, e.dataFinalizacao, e.dataEntrega));
    }

    /**
     * Copia o estado da OS para a entidade. A entidade existente é reaproveitada (e não recriada) para
     * preservar a versão do bloqueio otimista; as listas são regravadas por inteiro.
     */
    static void aplicar(OrdemServicoEntity e, OrdemServico os) {
        e.numero = os.getNumero();
        e.clienteId = os.getClienteId();
        e.veiculoId = os.getVeiculoId();
        e.status = os.getStatus();
        e.descricaoProblema = os.getDescricaoProblema();
        e.observacoes = os.getObservacoes();
        e.orcamentoId = os.getOrcamentoId();
        e.motivoCancelamento = os.getMotivoCancelamento();
        e.dataAbertura = os.getDataAbertura();
        e.dataInicio = os.getDataInicio();
        e.dataFinalizacao = os.getDataFinalizacao();
        e.dataEntrega = os.getDataEntrega();

        e.itensServico.clear();
        os.getItensServico().forEach(i ->
                e.itensServico.add(new ItemServicoEmb(i.servicoId(), i.nome(), i.quantidade(), i.precoUnitario())));
        e.itensPeca.clear();
        os.getItensPeca().forEach(i ->
                e.itensPeca.add(new ItemPecaEmb(i.pecaId(), i.codigo(), i.nome(), i.quantidade(), i.precoUnitario())));
        e.historico.clear();
        os.getHistorico().forEach(h ->
                e.historico.add(new HistoricoEmb(h.de(), h.para(), h.em(), h.motivo())));
    }
}
