package br.com.oficina.os.domain.model;

import br.com.oficina.os.domain.exception.RegraDeNegocioException;

import java.util.regex.Pattern;

/**
 * Cliente da oficina. O identificador é o próprio documento (CPF ou CNPJ, só dígitos): não existe id numérico
 * gerado pelo banco, e é esse valor que Veículo, OS, token do cliente e mensagens da Saga carregam.
 */
public record Cliente(Documento documento, String nome, String email,
                      String telefone, String endereco, boolean ativo) {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Cliente {
        if (documento == null) {
            throw new RegraDeNegocioException("Documento do cliente é obrigatório");
        }
        if (nome == null || nome.isBlank()) {
            throw new RegraDeNegocioException("Nome do cliente é obrigatório");
        }
        if (email != null && !email.isBlank() && !EMAIL.matcher(email).matches()) {
            throw new RegraDeNegocioException("E-mail inválido: " + email);
        }
    }

    public static Cliente novo(String nome, String documento, String email, String telefone, String endereco) {
        return new Cliente(Documento.of(documento), nome, email, telefone, endereco, true);
    }

    /** Identificador do cliente: o CPF/CNPJ só com dígitos. */
    public String id() {
        return documento.numero();
    }

    public Cliente desativar() {
        return new Cliente(documento, nome, email, telefone, endereco, false);
    }

    public Cliente atualizar(String novoNome, String novoEmail, String novoTelefone, String novoEndereco) {
        return new Cliente(documento, novoNome, novoEmail, novoTelefone, novoEndereco, ativo);
    }
}
