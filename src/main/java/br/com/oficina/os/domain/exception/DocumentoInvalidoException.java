package br.com.oficina.os.domain.exception;

/** CPF ou CNPJ inválido. */
public class DocumentoInvalidoException extends RegraDeNegocioException {

    public DocumentoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
