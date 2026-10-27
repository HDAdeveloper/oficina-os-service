package br.com.oficina.os.domain.exception;

import br.com.oficina.os.domain.model.StatusOS;

/** Tentativa de mover a OS para um status que o fluxo não permite. */
public class TransicaoStatusInvalidaException extends RegraDeNegocioException {

    public TransicaoStatusInvalidaException(StatusOS de, StatusOS para) {
        super("Transição inválida: " + de.getDescricao() + " → " + para.getDescricao());
    }
}
