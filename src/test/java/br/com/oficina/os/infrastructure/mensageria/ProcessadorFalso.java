package br.com.oficina.os.infrastructure.mensageria;

import br.com.oficina.os.application.port.in.ContextoMensagem;
import br.com.oficina.os.application.port.in.EventoSaga;
import br.com.oficina.os.application.port.in.ProcessadorEventos;

import java.util.ArrayList;
import java.util.List;

/** Orquestrador de mentira: guarda o que recebeu e pode ser instruído a falhar. */
class ProcessadorFalso implements ProcessadorEventos {

    final List<ContextoMensagem> contextos = new ArrayList<>();
    final List<EventoSaga> eventos = new ArrayList<>();
    RuntimeException falha;

    @Override
    public void processar(ContextoMensagem contexto, EventoSaga evento) {
        if (falha != null) {
            throw falha;
        }
        contextos.add(contexto);
        eventos.add(evento);
    }

    @Override
    public int verificarPrazos() {
        return 0;
    }
}
