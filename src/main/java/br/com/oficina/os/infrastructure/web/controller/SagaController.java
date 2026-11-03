package br.com.oficina.os.infrastructure.web.controller;

import br.com.oficina.os.application.port.in.GestaoOrdensServico;
import br.com.oficina.os.infrastructure.web.dto.SagaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Visão geral das Sagas, usada pelo painel {@code /saga-monitor.html}. Só ADMIN. */
@RestController
@RequestMapping("/api/sagas")
@Tag(name = "Sagas (acompanhamento)")
public class SagaController {

    private final GestaoOrdensServico ordens;

    public SagaController(GestaoOrdensServico ordens) {
        this.ordens = ordens;
    }

    @GetMapping
    @Operation(summary = "Últimas Sagas de todas as OS (mexidas há menos tempo primeiro)",
            description = "limite padrão 30, máximo 100. Cada item traz o estado, o link de pagamento e o diário.")
    public List<SagaResponse> recentes(@RequestParam(defaultValue = "30") int limite) {
        return ordens.listarSagasRecentes(limite).stream().map(SagaResponse::de).toList();
    }
}
