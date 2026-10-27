package br.com.oficina.os.infrastructure.config;

import br.com.oficina.os.application.port.out.GeradorId;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GeradorIdUuid implements GeradorId {
    @Override
    public String novo() {
        return UUID.randomUUID().toString();
    }
}
