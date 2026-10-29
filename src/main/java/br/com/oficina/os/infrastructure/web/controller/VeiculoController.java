package br.com.oficina.os.infrastructure.web.controller;

import br.com.oficina.os.application.port.in.GestaoVeiculos;
import br.com.oficina.os.application.port.in.GestaoVeiculos.AlteracaoVeiculo;
import br.com.oficina.os.application.port.in.GestaoVeiculos.NovoVeiculo;
import br.com.oficina.os.infrastructure.web.dto.VeiculoAtualizacaoRequest;
import br.com.oficina.os.infrastructure.web.dto.VeiculoRequest;
import br.com.oficina.os.infrastructure.web.dto.VeiculoResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/veiculos")
@Tag(name = "Veículos")
public class VeiculoController {

    private final GestaoVeiculos veiculos;

    public VeiculoController(GestaoVeiculos veiculos) {
        this.veiculos = veiculos;
    }

    @PostMapping
    @Operation(summary = "Cadastra um veículo de um cliente (placa antiga ou Mercosul)")
    public ResponseEntity<VeiculoResponse> cadastrar(@Valid @RequestBody VeiculoRequest req) {
        var criado = VeiculoResponse.de(veiculos.cadastrar(
                new NovoVeiculo(req.placa(), req.marca(), req.modelo(), req.ano(), req.clienteId())));
        var local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(local).body(criado);
    }

    @GetMapping
    @Operation(summary = "Lista os veículos ativos, ou os de um cliente se clienteId for informado")
    public List<VeiculoResponse> listar(@RequestParam(required = false) String clienteId) {
        var lista = clienteId == null || clienteId.isBlank() ? veiculos.listar() : veiculos.listarPorCliente(clienteId);
        return lista.stream().map(VeiculoResponse::de).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um veículo pelo id")
    public VeiculoResponse buscar(@PathVariable Long id) {
        return VeiculoResponse.de(veiculos.buscarPorId(id));
    }

    @GetMapping("/placa/{placa}")
    @Operation(summary = "Busca um veículo pela placa")
    public VeiculoResponse buscarPorPlaca(@PathVariable String placa) {
        return VeiculoResponse.de(veiculos.buscarPorPlaca(placa));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza marca, modelo e ano (a placa e o dono não mudam)")
    public VeiculoResponse atualizar(@PathVariable Long id, @Valid @RequestBody VeiculoAtualizacaoRequest req) {
        return VeiculoResponse.de(veiculos.atualizar(id,
                new AlteracaoVeiculo(req.marca(), req.modelo(), req.ano())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desativa o veículo")
    public ResponseEntity<Void> desativar(@PathVariable Long id) {
        veiculos.desativar(id);
        return ResponseEntity.noContent().build();
    }
}
