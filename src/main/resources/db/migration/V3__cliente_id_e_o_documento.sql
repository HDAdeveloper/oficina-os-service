-- O identificador do cliente passa a ser o proprio documento (CPF ou CNPJ, so digitos).
-- Veiculo e ordem_servico passam a apontar para cliente.documento. Os dados existentes sao convertidos.

-- 1) novas colunas com o documento do dono, preenchidas a partir do id antigo
ALTER TABLE veiculo       ADD COLUMN cliente_doc VARCHAR(14);
ALTER TABLE ordem_servico ADD COLUMN cliente_doc VARCHAR(14);

UPDATE veiculo v       SET cliente_doc = c.documento FROM cliente c WHERE c.id = v.cliente_id;
UPDATE ordem_servico o SET cliente_doc = c.documento FROM cliente c WHERE c.id = o.cliente_id;

-- 2) remove as colunas antigas (as chaves estrangeiras e os indices que dependem delas caem junto)
ALTER TABLE veiculo       DROP COLUMN cliente_id;
ALTER TABLE ordem_servico DROP COLUMN cliente_id;

-- 3) cliente: o documento vira a chave primaria e o id numerico deixa de existir
ALTER TABLE cliente DROP CONSTRAINT uk_cliente_documento;
ALTER TABLE cliente DROP COLUMN id;
ALTER TABLE cliente ADD CONSTRAINT pk_cliente PRIMARY KEY (documento);

-- 4) as novas colunas assumem o nome e as regras das antigas
ALTER TABLE veiculo       RENAME COLUMN cliente_doc TO cliente_id;
ALTER TABLE ordem_servico RENAME COLUMN cliente_doc TO cliente_id;

ALTER TABLE veiculo       ALTER COLUMN cliente_id SET NOT NULL;
ALTER TABLE ordem_servico ALTER COLUMN cliente_id SET NOT NULL;

ALTER TABLE veiculo       ADD CONSTRAINT fk_veiculo_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (documento);
ALTER TABLE ordem_servico ADD CONSTRAINT fk_os_cliente      FOREIGN KEY (cliente_id) REFERENCES cliente (documento);

CREATE INDEX idx_veiculo_cliente ON veiculo (cliente_id);
CREATE INDEX idx_os_cliente      ON ordem_servico (cliente_id);
