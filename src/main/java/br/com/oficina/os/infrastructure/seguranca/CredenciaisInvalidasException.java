package br.com.oficina.os.infrastructure.seguranca;

public class CredenciaisInvalidasException extends RuntimeException {
    public CredenciaisInvalidasException() {
        super("Usuário ou senha inválidos");
    }
}
