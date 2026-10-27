-- Recuperação de senha
ALTER TABLE usuarios ADD COLUMN reset_token VARCHAR(255);
ALTER TABLE usuarios ADD COLUMN reset_token_expira TIMESTAMP;

-- Gamificação (XP, nível, streak)
ALTER TABLE usuarios ADD COLUMN xp INTEGER NOT NULL DEFAULT 0;
ALTER TABLE usuarios ADD COLUMN nivel INTEGER NOT NULL DEFAULT 1;
ALTER TABLE usuarios ADD COLUMN streak_atual INTEGER NOT NULL DEFAULT 0;
ALTER TABLE usuarios ADD COLUMN ultima_atividade DATE;

-- Repetição espaçada (SM-2) nos flashcards
ALTER TABLE flashcards ADD COLUMN intervalo_dias INTEGER NOT NULL DEFAULT 0;
ALTER TABLE flashcards ADD COLUMN fator_facilidade DOUBLE PRECISION NOT NULL DEFAULT 2.5;
ALTER TABLE flashcards ADD COLUMN repeticoes INTEGER NOT NULL DEFAULT 0;
ALTER TABLE flashcards ADD COLUMN proxima_revisao DATE NOT NULL DEFAULT CURRENT_DATE;
