package br.com.oficina.os.infrastructure.web.controller;

import br.com.oficina.os.application.port.in.GestaoOrdensServico;
import br.com.oficina.os.infrastructure.web.dto.OrdemServicoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Área do cliente final: o token emitido pela Lambda de CPF (papel CLIENTE) só enxerga as próprias OS. */
@RestController
@RequestMapping("/api/minhas-ordens")
@Tag(name = "Área do cliente")
public class MinhasOrdensController {

    private final GestaoOrdensServico ordens;

    public MinhasOrdensController(GestaoOrdensServico ordens) {
        this.ordens = ordens;
    }

    @GetMapping
    @Operation(summary = "Ordens de serviço do cliente autenticado por CPF (acompanhamento do status)")
    public List<OrdemServicoResponse> minhasOrdens(@AuthenticationPrincipal Jwt jwt) {
        return ordens.listarDoCliente(jwt.getSubject()).stream().map(OrdemServicoResponse::de).toList();
    }
}
