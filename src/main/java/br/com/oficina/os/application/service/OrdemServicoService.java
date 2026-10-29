package br.com.oficina.os.application.service;

import br.com.oficina.os.application.port.in.GestaoOrdensServico;
import br.com.oficina.os.application.port.out.ClienteRepository;
import br.com.oficina.os.application.port.out.GeradorNumeroOS;
import br.com.oficina.os.application.port.out.OrdemServicoRepository;
import br.com.oficina.os.application.port.out.PecaRepository;
import br.com.oficina.os.application.port.out.ServicoRepository;
import br.com.oficina.os.application.port.out.VeiculoRepository;
import br.com.oficina.os.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.os.domain.exception.RegraDeNegocioException;
import br.com.oficina.os.domain.model.Cliente;
import br.com.oficina.os.domain.model.Documento;
import br.com.oficina.os.domain.model.HistoricoStatus;
import br.com.oficina.os.domain.model.OrdemServico;
import br.com.oficina.os.domain.model.Peca;
import br.com.oficina.os.domain.model.Placa;
import br.com.oficina.os.domain.model.Saga;
import br.com.oficina.os.domain.model.Servico;
import br.com.oficina.os.domain.model.StatusOS;
import br.com.oficina.os.domain.model.Veiculo;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class OrdemServicoService implements GestaoOrdensServico {

    private final OrdemServicoRepository ordens;
    private final ClienteRepository clientes;
    private final VeiculoRepository veiculos;
    private final ServicoRepository servicos;
    private final PecaRepository pecas;
    private final GeradorNumeroOS gerador;
    private final OrquestradorSaga saga;
    private final Clock clock;

    public OrdemServicoService(OrdemServicoRepository ordens, ClienteRepository clientes,
                               VeiculoRepository veiculos, ServicoRepository servicos,
                               PecaRepository pecas, GeradorNumeroOS gerador,
                               OrquestradorSaga saga, Clock clock) {
        this.ordens = ordens;
        this.clientes = clientes;
        this.veiculos = veiculos;
        this.servicos = servicos;
        this.pecas = pecas;
        this.gerador = gerador;
        this.saga = saga;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- abertura

    @Override
    public OrdemServico abrir(AberturaOS dados) {
        Cliente cliente = clientes.buscarPorId(Documento.normalizar(dados.documentoCliente()))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Cliente não encontrado com o documento: " + dados.documentoCliente()));
        if (!cliente.ativo()) {
            throw new RegraDeNegocioException("Cliente inativo: " + cliente.nome());
        }

        Veiculo veiculo = veiculos.buscarPorPlaca(Placa.normalizar(dados.placaVeiculo()))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Veículo não encontrado com a placa: " + dados.placaVeiculo()));
        if (!veiculo.ativo()) {
            throw new RegraDeNegocioException("Veículo inativo: " + veiculo.placa().valor());
        }
        if (!veiculo.clienteId().equals(cliente.id())) {
            throw new RegraDeNegocioException("O veículo " + veiculo.placa().valor()
                    + " não pertence ao cliente informado");
        }

        OrdemServico os = OrdemServico.abrir(gerador.proximo(), cliente.id(), veiculo.id(),
                dados.descricaoProblema(), dados.observacoes(), agora());

        if (dados.servicos() != null) {
            for (ItemSolicitado item : dados.servicos()) {
                os.adicionarServico(servicoPorId(item.itemId()), item.quantidade());
            }
        }
        if (dados.pecas() != null) {
            for (ItemSolicitado item : dados.pecas()) {
                os.adicionarPeca(pecaPorId(item.itemId()), item.quantidade());
            }
        }
        return ordens.salvar(os);
    }

    // ---------------------------------------------------------------- consultas

    @Override
    public OrdemServico buscarPorId(Long id) {
        return ordens.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de Serviço não encontrada: " + id));
    }

    @Override
    public StatusOS consultarStatus(Long id) {
        return buscarPorId(id).getStatus();
    }

    @Override
    public List<HistoricoStatus> consultarHistorico(Long id) {
        return buscarPorId(id).getHistorico();
    }

    @Override
    public List<OrdemServico> listarAtivas() {
        List<StatusOS> ativos = java.util.Arrays.stream(StatusOS.values()).filter(StatusOS::isAtiva).toList();
        return ordens.buscarPorStatus(ativos).stream()
                .sorted(Comparator
                        .<OrdemServico>comparingInt(os -> os.getStatus().prioridade())
                        .thenComparing(OrdemServico::getDataAbertura))
                .toList();
    }

    @Override
    public List<OrdemServico> listarPorStatus(StatusOS status) {
        return ordens.buscarPorStatus(EnumSet.of(status));
    }

    @Override
    public List<OrdemServico> listarDoCliente(String documento) {
        Cliente cliente = clientes.buscarPorId(Documento.normalizar(documento))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Cliente não encontrado com o documento: " + documento));
        return ordens.buscarPorCliente(cliente.id());
    }

    // ---------------------------------------------------------------- fluxo

    @Override
    public OrdemServico iniciarDiagnostico(Long id) {
        OrdemServico os = buscarPorId(id);
        os.iniciarDiagnostico(agora());
        return ordens.salvar(os);
    }

    @Override
    public OrdemServico adicionarServico(Long id, ItemSolicitado item) {
        OrdemServico os = buscarPorId(id);
        os.adicionarServico(servicoPorId(item.itemId()), item.quantidade());
        return ordens.salvar(os);
    }

    @Override
    public OrdemServico adicionarPeca(Long id, ItemSolicitado item) {
        OrdemServico os = buscarPorId(id);
        os.adicionarPeca(pecaPorId(item.itemId()), item.quantidade());
        return ordens.salvar(os);
    }

    @Override
    public OrdemServico solicitarOrcamento(Long id) {
        OrdemServico os = buscarPorId(id);
        os.validarParaOrcamento();
        Cliente cliente = clientes.buscarPorId(os.getClienteId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Cliente não encontrado: " + os.getClienteId()));
        saga.solicitarOrcamento(os, cliente);
        return os;
    }

    @Override
    public OrdemServico aprovarOrcamento(Long id, String metodoPagamento) {
        OrdemServico os = buscarPorId(id);
        exigirAguardandoAprovacao(os, "aprovado");
        saga.cobrar(os, metodoPagamento);
        return os;
    }

    @Override
    public List<Saga> consultarSagas(Long id) {
        buscarPorId(id);
        return saga.sagasDaOs(id);
    }

    @Override
    public OrdemServico recusarOrcamento(Long id, String motivo) {
        OrdemServico os = buscarPorId(id);
        exigirAguardandoAprovacao(os, "recusado");
        saga.exigirAguardandoAprovacao(os, "recusado");
        String orcamentoId = os.getOrcamentoId();
        String motivoFinal = (motivo == null || motivo.isBlank()) ? "Orçamento recusado pelo cliente" : motivo;
        os.voltarParaDiagnostico(motivoFinal, agora());
        OrdemServico salva = ordens.salvar(os);
        saga.recusar(salva, orcamentoId, motivoFinal);
        return salva;
    }

    @Override
    public OrdemServico cancelar(Long id, String motivo) {
        OrdemServico os = buscarPorId(id);
        if (os.getStatus() != StatusOS.RECEBIDA && os.getStatus() != StatusOS.EM_DIAGNOSTICO) {
            throw new RegraDeNegocioException("Cancelamento manual só é permitido com a OS Recebida ou Em "
                    + "Diagnóstico. Status atual: " + os.getStatus().getDescricao());
        }
        os.cancelar(motivo, agora());
        OrdemServico salva = ordens.salvar(os);
        saga.cancelamentoManual(salva.getId(), motivo);
        return salva;
    }

    @Override
    public OrdemServico entregar(Long id) {
        OrdemServico os = buscarPorId(id);
        os.entregar(agora());
        return ordens.salvar(os);
    }

    // ---------------------------------------------------------------- apoio

    private void exigirAguardandoAprovacao(OrdemServico os, String acao) {
        if (os.getStatus() != StatusOS.AGUARDANDO_APROVACAO) {
            throw new RegraDeNegocioException("O orçamento só pode ser " + acao
                    + " com a OS Aguardando Aprovação. Status atual: " + os.getStatus().getDescricao());
        }
    }

    private Servico servicoPorId(Long id) {
        return servicos.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado: " + id));
    }

    private Peca pecaPorId(Long id) {
        return pecas.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Peça não encontrada: " + id));
    }

    private Instant agora() {
        return Instant.now(clock);
    }
}
