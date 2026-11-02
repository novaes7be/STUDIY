-- O id simulado ("SIMULADO-" + UUID) tem 45 caracteres e estourava VARCHAR(40).
ALTER TABLE pedidos ALTER COLUMN mp_payment_id TYPE VARCHAR(64);
