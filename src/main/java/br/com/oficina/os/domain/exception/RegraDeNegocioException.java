package br.com.oficina.os.domain.exception;

/** Violação de uma regra de negócio. Vira HTTP 422 na camada REST. */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
