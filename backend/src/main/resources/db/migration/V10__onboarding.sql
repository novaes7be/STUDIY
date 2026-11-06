-- Onboarding do primeiro acesso, controlado pelo backend. Contas existentes já passaram por ele.
ALTER TABLE usuarios ADD COLUMN onboarding_concluido BOOLEAN NOT NULL DEFAULT TRUE;
