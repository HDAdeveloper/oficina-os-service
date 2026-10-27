package br.com.oficina.os.domain.exception;

/** Tentativa de criar algo que já existe (documento, placa, código de peça). Vira HTTP 409. */
public class ConflitoException extends RegraDeNegocioException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
