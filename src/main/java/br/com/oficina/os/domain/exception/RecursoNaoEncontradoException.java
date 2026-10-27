package br.com.oficina.os.domain.exception;

/** Cliente, veículo, serviço, peça ou OS inexistente. Vira HTTP 404. */
public class RecursoNaoEncontradoException extends RegraDeNegocioException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
