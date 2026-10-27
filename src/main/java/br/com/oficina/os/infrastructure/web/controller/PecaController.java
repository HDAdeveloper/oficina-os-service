package br.com.oficina.os.infrastructure.web.controller;

import br.com.oficina.os.application.port.in.GestaoPecas;
import br.com.oficina.os.application.port.in.GestaoPecas.AlteracaoPeca;
import br.com.oficina.os.application.port.in.GestaoPecas.NovaPeca;
import br.com.oficina.os.infrastructure.web.dto.PecaAtualizacaoRequest;
import br.com.oficina.os.infrastructure.web.dto.PecaRequest;
import br.com.oficina.os.infrastructure.web.dto.PecaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/pecas")
@Tag(name = "Peças (catálogo)")
public class PecaController {

    private final GestaoPecas pecas;

    public PecaController(GestaoPecas pecas) {
        this.pecas = pecas;
    }

    @PostMapping
    @Operation(summary = "Cadastra uma peça no catálogo (código único e preço; a quantidade em estoque fica no Execution Service)")
    public ResponseEntity<PecaResponse> cadastrar(@Valid @RequestBody PecaRequest req) {
        var criada = PecaResponse.de(pecas.cadastrar(
                new NovaPeca(req.codigo(), req.nome(), req.descricao(), req.precoUnitario())));
        var local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(criada.id()).toUri();
        return ResponseEntity.created(local).body(criada);
    }

    @GetMapping
    @Operation(summary = "Lista as peças ativas")
    public List<PecaResponse> listar() {
        return pecas.listar().stream().map(PecaResponse::de).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca uma peça pelo id")
    public PecaResponse buscar(@PathVariable Long id) {
        return PecaResponse.de(pecas.buscarPorId(id));
    }

    @GetMapping("/codigo/{codigo}")
    @Operation(summary = "Busca uma peça pelo código")
    public PecaResponse buscarPorCodigo(@PathVariable String codigo) {
        return PecaResponse.de(pecas.buscarPorCodigo(codigo));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza nome, descrição e preço (o código não muda)")
    public PecaResponse atualizar(@PathVariable Long id, @Valid @RequestBody PecaAtualizacaoRequest req) {
        return PecaResponse.de(pecas.atualizar(id,
                new AlteracaoPeca(req.nome(), req.descricao(), req.precoUnitario())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desativa a peça")
    public ResponseEntity<Void> desativar(@PathVariable Long id) {
        pecas.desativar(id);
        return ResponseEntity.noContent().build();
    }
}
