-- Idioma do site que a pessoa usa (pt-BR, en, es). Contas existentes ficam em português.
ALTER TABLE usuarios ADD COLUMN idioma VARCHAR(5) NOT NULL DEFAULT 'pt-BR';
