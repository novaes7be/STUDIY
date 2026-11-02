-- Revogação de sessões (logout / troca de senha incrementam a versão do token)
ALTER TABLE usuarios ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;

-- Registro do aceite de termos/privacidade (LGPD)
ALTER TABLE usuarios ADD COLUMN termos_aceitos_em TIMESTAMP;
ALTER TABLE usuarios ADD COLUMN termos_versao VARCHAR(20);

-- Token de recuperação passa a ser guardado apenas como hash SHA-256
ALTER TABLE usuarios RENAME COLUMN reset_token TO reset_token_hash;
UPDATE usuarios SET reset_token_hash = NULL, reset_token_expira = NULL;
CREATE INDEX idx_usuarios_reset_hash ON usuarios(reset_token_hash);

-- Cor da matéria agora é uma chave de paleta (azul, roxo, vermelho, grafite)
UPDATE materias SET cor = 'azul' WHERE cor NOT IN ('azul','roxo','vermelho','grafite');
ALTER TABLE materias ALTER COLUMN cor SET DEFAULT 'azul';
