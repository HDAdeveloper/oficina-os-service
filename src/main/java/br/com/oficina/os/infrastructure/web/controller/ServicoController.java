package br.com.oficina.os.infrastructure.web.controller;

import br.com.oficina.os.application.port.in.GestaoServicos;
import br.com.oficina.os.application.port.in.GestaoServicos.AlteracaoServico;
import br.com.oficina.os.application.port.in.GestaoServicos.NovoServico;
import br.com.oficina.os.infrastructure.web.dto.ServicoRequest;
import br.com.oficina.os.infrastructure.web.dto.ServicoResponse;
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
@RequestMapping("/api/servicos")
@Tag(name = "Serviços (catálogo)")
public class ServicoController {

    private final GestaoServicos servicos;

    public ServicoController(GestaoServicos servicos) {
        this.servicos = servicos;
    }

    @PostMapping
    @Operation(summary = "Cadastra um serviço no catálogo (nome e preço)")
    public ResponseEntity<ServicoResponse> cadastrar(@Valid @RequestBody ServicoRequest req) {
        var criado = ServicoResponse.de(servicos.cadastrar(
                new NovoServico(req.nome(), req.descricao(), req.preco(), req.tempoEstimadoMinutos())));
        var local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(local).body(criado);
    }

    @GetMapping
    @Operation(summary = "Lista os serviços ativos")
    public List<ServicoResponse> listar() {
        return servicos.listar().stream().map(ServicoResponse::de).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um serviço pelo id")
    public ServicoResponse buscar(@PathVariable Long id) {
        return ServicoResponse.de(servicos.buscarPorId(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza um serviço (OS já abertas mantêm o preço da época)")
    public ServicoResponse atualizar(@PathVariable Long id, @Valid @RequestBody ServicoRequest req) {
        return ServicoResponse.de(servicos.atualizar(id,
                new AlteracaoServico(req.nome(), req.descricao(), req.preco(), req.tempoEstimadoMinutos())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desativa o serviço")
    public ResponseEntity<Void> desativar(@PathVariable Long id) {
        servicos.desativar(id);
        return ResponseEntity.noContent().build();
    }
}
