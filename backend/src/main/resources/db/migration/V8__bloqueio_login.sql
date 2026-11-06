-- Bloqueio progressivo por conta após falhas de login (além do rate limit por IP).
ALTER TABLE usuarios ADD COLUMN falhas_login INTEGER NOT NULL DEFAULT 0;
ALTER TABLE usuarios ADD COLUMN bloqueado_ate TIMESTAMP;
