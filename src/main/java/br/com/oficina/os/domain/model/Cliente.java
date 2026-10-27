package br.com.oficina.os.domain.model;

import br.com.oficina.os.domain.exception.RegraDeNegocioException;

import java.util.regex.Pattern;

public record Cliente(Long id, String nome, Documento documento, String email,
                      String telefone, String endereco, boolean ativo) {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Cliente {
        if (nome == null || nome.isBlank()) {
            throw new RegraDeNegocioException("Nome do cliente é obrigatório");
        }
        if (documento == null) {
            throw new RegraDeNegocioException("Documento do cliente é obrigatório");
        }
        if (email != null && !email.isBlank() && !EMAIL.matcher(email).matches()) {
            throw new RegraDeNegocioException("E-mail inválido: " + email);
        }
    }

    public static Cliente novo(String nome, String documento, String email, String telefone, String endereco) {
        return new Cliente(null, nome, Documento.of(documento), email, telefone, endereco, true);
    }

    public Cliente desativar() {
        return new Cliente(id, nome, documento, email, telefone, endereco, false);
    }

    public Cliente atualizar(String novoNome, String novoEmail, String novoTelefone, String novoEndereco) {
        return new Cliente(id, novoNome, documento, novoEmail, novoTelefone, novoEndereco, ativo);
    }
}
