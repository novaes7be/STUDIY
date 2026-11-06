-- Verificação de e-mail. Contas existentes já nascem verificadas (DEFAULT TRUE).
ALTER TABLE usuarios ADD COLUMN email_verificado BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE usuarios ADD COLUMN verif_token_hash VARCHAR(64);
ALTER TABLE usuarios ADD COLUMN verif_token_expira TIMESTAMP;
CREATE INDEX idx_usuarios_verif_token ON usuarios (verif_token_hash);
