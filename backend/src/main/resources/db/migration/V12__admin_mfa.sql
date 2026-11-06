-- Papel (USUARIO/ADMIN) e MFA por TOTP. O segredo TOTP fica cifrado (AES-GCM, CriptoConverter).
-- Promover a admin é manual, direto no banco (veja README): nenhuma rota da API altera papel.
ALTER TABLE usuarios ADD COLUMN papel VARCHAR(20) NOT NULL DEFAULT 'USUARIO';
ALTER TABLE usuarios ADD COLUMN mfa_segredo TEXT;
ALTER TABLE usuarios ADD COLUMN mfa_ativo BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuarios ADD COLUMN mfa_ultimo_passo BIGINT;
-- A sessão (e suas renovações) lembra se o segundo fator foi verificado.
ALTER TABLE sessoes_refresh ADD COLUMN mfa BOOLEAN NOT NULL DEFAULT FALSE;
