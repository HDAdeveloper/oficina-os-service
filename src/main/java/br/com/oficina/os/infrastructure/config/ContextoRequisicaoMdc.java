package br.com.oficina.os.infrastructure.config;

import br.com.oficina.os.application.port.out.ContextoRequisicao;
import br.com.oficina.os.infrastructure.web.filtro.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** O correlationId da requisição atual (posto no MDC pelo filtro); fora de uma requisição gera um novo. */
@Component
public class ContextoRequisicaoMdc implements ContextoRequisicao {
    @Override
    public String correlationId() {
        String id = MDC.get(CorrelationIdFilter.CHAVE_MDC);
        return id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
    }
}
