package br.com.oficina.os.infrastructure.web.controller;

import br.com.oficina.os.application.port.in.GestaoClientes;
import br.com.oficina.os.application.port.in.GestaoClientes.AlteracaoCliente;
import br.com.oficina.os.application.port.in.GestaoClientes.NovoCliente;
import br.com.oficina.os.infrastructure.web.dto.ClienteAtualizacaoRequest;
import br.com.oficina.os.infrastructure.web.dto.ClienteRequest;
import br.com.oficina.os.infrastructure.web.dto.ClienteResponse;
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
@RequestMapping("/api/clientes")
@Tag(name = "Clientes")
public class ClienteController {

    private final GestaoClientes clientes;

    public ClienteController(GestaoClientes clientes) {
        this.clientes = clientes;
    }

    @PostMapping
    @Operation(summary = "Cadastra um cliente (CPF ou CNPJ, com ou sem pontuação)")
    public ResponseEntity<ClienteResponse> cadastrar(@Valid @RequestBody ClienteRequest req) {
        var criado = ClienteResponse.de(clientes.cadastrar(
                new NovoCliente(req.nome(), req.documento(), req.email(), req.telefone(), req.endereco())));
        var local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(local).body(criado);
    }

    @GetMapping
    @Operation(summary = "Lista os clientes ativos")
    public List<ClienteResponse> listar() {
        return clientes.listar().stream().map(ClienteResponse::de).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um cliente pelo id (que é o CPF/CNPJ, com ou sem pontuação)")
    public ClienteResponse buscar(@PathVariable String id) {
        return ClienteResponse.de(clientes.buscarPorId(id));
    }

    @GetMapping("/documento/{documento}")
    @Operation(summary = "Busca um cliente pelo CPF/CNPJ")
    public ClienteResponse buscarPorDocumento(@PathVariable String documento) {
        return ClienteResponse.de(clientes.buscarPorDocumento(documento));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza os dados de contato do cliente (o documento não muda)")
    public ClienteResponse atualizar(@PathVariable String id, @Valid @RequestBody ClienteAtualizacaoRequest req) {
        return ClienteResponse.de(clientes.atualizar(id,
                new AlteracaoCliente(req.nome(), req.email(), req.telefone(), req.endereco())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desativa o cliente (o histórico é preservado)")
    public ResponseEntity<Void> desativar(@PathVariable String id) {
        clientes.desativar(id);
        return ResponseEntity.noContent().build();
    }
}
