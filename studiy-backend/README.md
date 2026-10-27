# Studiy Backend

API em Java + Spring Boot para o Studiy: autenticação (JWT), CRUD de matérias,
upload de PDF (com extração de texto via Apache PDFBox) e geração de
resumo/flashcards/simulado via IA (Claude Haiku), com controle de uso mensal
por plano (FREE/PREMIUM).

## Rodando localmente

1. Configure o PostgreSQL (veja o passo a passo enviado à parte) e crie o banco `studiy`.
2. Ajuste `src/main/resources/application.properties` se seu usuário/senha do
   Postgres forem diferentes de `postgres`/`postgres`.
3. Defina as variáveis de ambiente antes de rodar:
   - `ANTHROPIC_API_KEY` — sua chave da API da Anthropic
   - `JWT_SECRET`, `STUDIY_ENCRYPTION_KEY`, `DB_*`, `CORS_ORIGINS` — veja `.env.example` (em dev há valores de teste; em prod são obrigatórios)
4. Rode:
   ```
   ./mvnw spring-boot:run
   ```
   A API sobe em `http://localhost:8080`.

## Migrations (Flyway)

O schema agora é controlado por Flyway (`src/main/resources/db/migration`), não mais
por `ddl-auto=update`. Ao rodar o projeto pela primeira vez contra um banco `studiy`
vazio, o Flyway cria tudo sozinho a partir de `V1__init.sql`. Novas mudanças de
schema entram como `V2__...sql`, `V3__...sql`, etc. — nunca edite um arquivo de
migration já aplicado.

Se você já tinha rodado o projeto antes (com `ddl-auto=update`), apague o banco e
recrie do zero pra não conflitar com o Flyway:
```sql
DROP DATABASE studiy;
CREATE DATABASE studiy;
```

## Endpoints principais

- `POST /api/auth/registrar` — { nome, email, senha (8-72), aceitoTermos: true }
- `POST /api/auth/login` — { email, senha } → retorna token JWT
- `POST /api/auth/logout` — encerra todas as sessões do usuário
- `POST /api/auth/esqueci-senha` / `POST /api/auth/redefinir-senha` — { email } / { token, novaSenha }
- `GET /api/usuarios/me` — perfil, uso do mês, XP/nível/streak
- `GET /api/usuarios/me/exportar` — exporta todos os dados do usuário (LGPD)
- `DELETE /api/usuarios/me` — { senha } exclui a conta e todo o conteúdo
- `GET /api/materias/{id}/pdfs` — lista PDFs da matéria
- `GET /api/materias` — lista matérias do usuário logado (Authorization: Bearer <token>)
- `POST /api/materias` — { nome, cor: azul|roxo|vermelho|grafite }
- `PUT /api/materias/{id}` — { nome, cor }
- `DELETE /api/materias/{id}`
- `POST /api/materias/{id}/pdfs` — multipart/form-data, campo `arquivo`
- `POST /api/materias/{id}/gerar` — { pdfId, tipo: RESUMO|FLASHCARD|SIMULADO }
- `GET /api/materias/{id}/resumos` — lista resumos já gerados pra essa matéria
- `GET /api/materias/{id}/atividades` — lista atividades (to-do) da matéria
- `POST /api/materias/{id}/atividades` — { titulo, dataAtividade, tag, tagTipo }
- `PATCH /api/materias/{id}/atividades/{atividadeId}/concluir` — alterna concluída/pendente
- `DELETE /api/materias/{id}/atividades/{atividadeId}`

## Onde o custo de IA é controlado

- `PdfService` extrai o texto do PDF no servidor — nunca mandamos o arquivo bruto pra IA.
- `UsoMensalService` bloqueia (HTTP 429) quando o usuário estoura o limite do plano
  (configurável em `application.properties`: `studiy.limits.free.pdfs-por-mes` e
  `studiy.limits.premium.pdfs-por-mes`).
- `AnthropicService` usa `claude-haiku-4-5-20251001` (modelo mais barato) e trunca
  textos muito longos antes de enviar.
- A chave da API só existe no backend (variável de ambiente), nunca no front-end.

## Novidades (passos 2 a 5)

- **Recuperar senha**: `POST /api/auth/esqueci-senha` { email } e `POST /api/auth/redefinir-senha` { token, novaSenha }.
  Em dev, o token volta na própria resposta (`studiy.auth.expor-token-reset-dev=true`); em produção, troque isso
  por envio de e-mail de verdade (TODO marcado no `AuthService`) e desligue essa flag.
- **Flashcards**: CRUD completo + repetição espaçada (SM-2) em `/api/materias/{id}/flashcards`.
  `GET /revisao` traz só os cards que vencem hoje; `POST /{id}/revisar` { qualidade: 0-5 } recalcula o intervalo.
- **Gamificação**: XP, nível e streak diário em `GET /api/gamificacao/me`. Dispara automaticamente no login,
  ao concluir uma atividade e ao gerar conteúdo com IA.
- **Upload de PDF**: agora valida tipo (`application/pdf`), tamanho (máx. 10MB) e rejeita PDFs vazios/escaneados
  com uma mensagem clara (HTTP 422).

## Próximos passos sugeridos

- Ligar o front-end estático (login.html, cadastro.html, dashboard.html) a esses
  endpoints via `fetch`, guardando o token JWT no `localStorage`.
- Adicionar endpoint de simulado "respondido" (salvar nota real depois que o
  aluno responde as questões geradas).
- Testes automatizados dos services principais.
