package br.com.oficina.os.domain.exception;

/** Placa fora dos padrões antigo (ABC1234) e Mercosul (ABC1D23). */
public class PlacaInvalidaException extends RegraDeNegocioException {

    public PlacaInvalidaException(String mensagem) {
        super(mensagem);
    }
}
