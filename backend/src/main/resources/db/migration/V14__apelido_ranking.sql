-- Apelido público do ranking. NULL = não participa do ranking (padrão).
-- Único sem diferenciar maiúsculas para ninguém se passar por outra pessoa.
ALTER TABLE usuarios ADD COLUMN apelido VARCHAR(20);
CREATE UNIQUE INDEX ux_usuarios_apelido_lower ON usuarios (LOWER(apelido)) WHERE apelido IS NOT NULL;
