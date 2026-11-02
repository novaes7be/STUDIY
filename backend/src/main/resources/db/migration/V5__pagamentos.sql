-- Plano Pro com validade (acesso por período pago)
ALTER TABLE usuarios ADD COLUMN plano_expira_em TIMESTAMP;

-- Pedidos de pagamento. Se a conta for excluída o registro financeiro fica, sem vínculo com a pessoa.
CREATE TABLE pedidos (
    id               VARCHAR(36) PRIMARY KEY,
    usuario_id       BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    metodo           VARCHAR(10) NOT NULL,
    valor_centavos   INTEGER     NOT NULL,
    status           VARCHAR(15) NOT NULL,
    mp_preference_id VARCHAR(100),
    mp_payment_id    VARCHAR(40) UNIQUE,
    criado_em        TIMESTAMP   NOT NULL DEFAULT now(),
    pago_em          TIMESTAMP
);
CREATE INDEX idx_pedidos_usuario ON pedidos(usuario_id);
