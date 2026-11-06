# Contribuindo

Obrigado pelo interesse! Regras curtas:

1. **Regra de ouro:** nenhuma regra de negócio, cálculo, permissão ou segredo no front. O front só
   exibe dados prontos da API; todo o resto fica no backend (e é testado lá).
2. **Migrations:** nunca edite uma migration Flyway que já existe; crie `V<N+1>__descricao.sql`.
3. **Testes:** toda mudança no backend vem com teste. `mvn -B verify` (em `backend/`) roda os
   unitários e os de integração (precisa de Docker, por causa do Testcontainers).
4. **Front:** dados da API sempre com `textContent` (nunca `innerHTML`). Teste com
   `deploy/build-front.sh dev`.
5. **Segredos:** nunca commite `.env`, chaves ou tokens. O CI roda gitleaks.
6. **Commits:** pequenos, no estilo `feat:`, `fix:`, `refactor:`, `test:`, `chore:`.
7. Abra um PR contra `main`; o CI (testes, CodeQL, gitleaks) precisa passar.

Achou uma vulnerabilidade? Siga o [SECURITY.md](SECURITY.md), não abra issue pública.
