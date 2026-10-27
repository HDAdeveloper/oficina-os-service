package br.com.oficina.os.infrastructure.web.controller;

import br.com.oficina.os.application.port.in.GestaoOrdensServico;
import br.com.oficina.os.application.port.in.GestaoOrdensServico.AberturaOS;
import br.com.oficina.os.application.port.in.GestaoOrdensServico.ItemSolicitado;
import br.com.oficina.os.domain.model.StatusOS;
import br.com.oficina.os.infrastructure.web.dto.AberturaOSRequest;
import br.com.oficina.os.infrastructure.web.dto.AprovacaoRequest;
import br.com.oficina.os.infrastructure.web.dto.SagaResponse;
import br.com.oficina.os.infrastructure.web.dto.HistoricoResponse;
import br.com.oficina.os.infrastructure.web.dto.ItemOSRequest;
import br.com.oficina.os.infrastructure.web.dto.MotivoRequest;
import br.com.oficina.os.infrastructure.web.dto.OrdemServicoResponse;
import br.com.oficina.os.infrastructure.web.dto.StatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/ordens-servico")
@Tag(name = "Ordens de Serviço")
public class OrdemServicoController {

    private final GestaoOrdensServico ordens;

    public OrdemServicoController(GestaoOrdensServico ordens) {
        this.ordens = ordens;
    }

    private static List<ItemSolicitado> itens(List<ItemOSRequest> lista) {
        return lista == null ? List.of() : lista.stream().map(i -> new ItemSolicitado(i.itemId(), i.quantidade())).toList();
    }

    // ------------------------------------------------------------ abertura e consultas

    @PostMapping
    @Operation(summary = "Abre uma OS para um cliente (CPF/CNPJ) e um veículo (placa), com serviços e peças opcionais")
    public ResponseEntity<OrdemServicoResponse> abrir(@Valid @RequestBody AberturaOSRequest req) {
        var criada = OrdemServicoResponse.de(ordens.abrir(new AberturaOS(req.documentoCliente(), req.placaVeiculo(),
                req.descricaoProblema(), req.observacoes(), itens(req.servicos()), itens(req.pecas()))));
        var local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(criada.id()).toUri();
        return ResponseEntity.created(local).body(criada);
    }

    @GetMapping
    @Operation(summary = "Lista as OS em andamento (mais prioritárias e antigas primeiro) ou filtra por status")
    public List<OrdemServicoResponse> listar(@RequestParam(required = false) StatusOS status) {
        var lista = status == null ? ordens.listarAtivas() : ordens.listarPorStatus(status);
        return lista.stream().map(OrdemServicoResponse::de).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca uma OS completa pelo id")
    public OrdemServicoResponse buscar(@PathVariable Long id) {
        return OrdemServicoResponse.de(ordens.buscarPorId(id));
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Consulta só o status da OS")
    public StatusResponse status(@PathVariable Long id) {
        return StatusResponse.de(ordens.consultarStatus(id));
    }

    @GetMapping("/{id}/historico")
    @Operation(summary = "Histórico de mudanças de status da OS")
    public List<HistoricoResponse> historico(@PathVariable Long id) {
        return ordens.consultarHistorico(id).stream().map(HistoricoResponse::de).toList();
    }

    // ------------------------------------------------------------ fluxo

    @PostMapping("/{id}/diagnostico")
    @Operation(summary = "Inicia o diagnóstico (RECEBIDA → EM_DIAGNOSTICO)")
    public OrdemServicoResponse iniciarDiagnostico(@PathVariable Long id) {
        return OrdemServicoResponse.de(ordens.iniciarDiagnostico(id));
    }

    @PostMapping("/{id}/servicos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Adiciona um serviço do catálogo à OS (antes do orçamento)")
    public OrdemServicoResponse adicionarServico(@PathVariable Long id, @Valid @RequestBody ItemOSRequest req) {
        return OrdemServicoResponse.de(ordens.adicionarServico(id, new ItemSolicitado(req.itemId(), req.quantidade())));
    }

    @PostMapping("/{id}/pecas")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Adiciona uma peça do catálogo à OS (antes do orçamento)")
    public OrdemServicoResponse adicionarPeca(@PathVariable Long id, @Valid @RequestBody ItemOSRequest req) {
        return OrdemServicoResponse.de(ordens.adicionarPeca(id, new ItemSolicitado(req.itemId(), req.quantidade())));
    }

    @PostMapping("/{id}/orcamento")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Pede o orçamento ao Billing (assíncrono: o status muda quando o Billing responde)")
    public OrdemServicoResponse solicitarOrcamento(@PathVariable Long id) {
        return OrdemServicoResponse.de(ordens.solicitarOrcamento(id));
    }

    @PostMapping("/{id}/orcamento/aprovacao")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Cliente aprova o orçamento: dispara a Saga (pagamento e reserva de estoque)",
            description = "Corpo opcional com metodoPagamento. No provedor simulado: APRO aprova, FUND/OTHE recusam, "
                    + "ERRO simula provedor fora do ar. Acompanhe o andamento em GET /{id}/saga.")
    public OrdemServicoResponse aprovarOrcamento(@PathVariable Long id,
                                                 @Valid @RequestBody(required = false) AprovacaoRequest req) {
        return OrdemServicoResponse.de(ordens.aprovarOrcamento(id, req == null ? null : req.metodoPagamento()));
    }

    @GetMapping("/{id}/saga")
    @Operation(summary = "Diário da Saga da OS (comandos, eventos e compensações), a mais recente primeiro")
    public List<SagaResponse> saga(@PathVariable Long id) {
        return ordens.consultarSagas(id).stream().map(SagaResponse::de).toList();
    }

    @PostMapping("/{id}/orcamento/recusa")
    @Operation(summary = "Cliente recusa o orçamento: a OS volta ao diagnóstico e o orçamento é cancelado")
    public OrdemServicoResponse recusarOrcamento(@PathVariable Long id,
                                                 @Valid @RequestBody(required = false) MotivoRequest req) {
        return OrdemServicoResponse.de(ordens.recusarOrcamento(id, req == null ? null : req.motivo()));
    }

    @PostMapping("/{id}/cancelamento")
    @Operation(summary = "Cancela a OS (só com a OS Recebida ou Em Diagnóstico)")
    public OrdemServicoResponse cancelar(@PathVariable Long id,
                                         @Valid @RequestBody(required = false) MotivoRequest req) {
        return OrdemServicoResponse.de(ordens.cancelar(id, req == null ? null : req.motivo()));
    }

    @PostMapping("/{id}/entrega")
    @Operation(summary = "Registra a entrega do veículo (FINALIZADA → ENTREGUE)")
    public OrdemServicoResponse entregar(@PathVariable Long id) {
        return OrdemServicoResponse.de(ordens.entregar(id));
    }
}
