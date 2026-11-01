<<<<<<< HEAD
# Studiy

Plataforma de estudos: organize matérias, envie PDFs e receba resumo + flashcards com revisão por repetição espaçada (SM-2).

```
STUDIY PROJECT/
├── STUDIY/src/            front estático (HTML/CSS/JS puro)
│   ├── css/tokens.css     ← TODAS as cores (constantes). Edite aqui para mudar o visual.
│   ├── css/style.css      estilos (usa só var(--...), nenhum hex)
│   └── js/                config, api, auth, dashboard, theme
└── studiy-backend/        API Spring Boot 3 + PostgreSQL + Flyway
```

## Rodar em desenvolvimento

1. PostgreSQL com o banco `studiy` (`DROP/CREATE` se vier de versões antigas; o Flyway aplica V1→V3 sozinho).
2. Backend (em dev funciona sem configurar nada: usa valores de teste públicos). Defina `DB_USER`/`DB_PASSWORD` se os seus forem diferentes de `postgres`:
   ```
   cd studiy-backend && ./mvnw spring-boot:run      # http://localhost:8080
   ```
3. Front: abra `STUDIY/src` com Live Server (porta 5500) ou `npx serve STUDIY/src -p 5500`.
   O endereço da API está em `STUDIY/src/js/config.js` (`API_BASE`).
4. Em dev, "Esqueci a senha" mostra o token na tela (`studiy.auth.expor-token-reset-dev=true`).
   Sem `ANTHROPIC_API_KEY`, a IA devolve respostas de teste.

## "Não foi possível conectar ao servidor" (login/cadastro)

O front (Live Server, porta 5500) só funciona com o **backend ligado na porta 8080**.

1. Suba o backend (`./mvnw spring-boot:run` ou o botão Run do IntelliJ) e espere a linha `Started StudiyBackendApplication`.
2. Abra `http://localhost:8080/api/saude` (ou `127.0.0.1`, o mesmo endereço que aparece no navegador do front). Deve mostrar `{"status":"ok"}`. Se não abrir, o problema é o backend, não o front.
3. Se o backend não sobe, olhe o console. Causas comuns:
   - **Banco**: o PostgreSQL precisa estar rodando, com o banco `studiy`, usuário/senha `postgres`/`postgres` (padrão de dev). Outros valores: variáveis `DB_USER` e `DB_PASSWORD`.
   - **Migração V3** falhou num banco antigo: recrie o banco `studiy` vazio.
   - Porta 8080 ocupada por outro programa.
4. O front detecta a API sozinho (`js/config.js`): usa o mesmo host da página na porta 8080. O CORS de dev já aceita qualquer porta de `localhost` e `127.0.0.1`.
5. As páginas avisam na tela quando o servidor está offline.

## Cores (tokens)

`css/tokens.css` tem 3 camadas: **primitivas** (`--c-*`), **semânticas** claro/escuro (`--text`, `--surface`, `--hero-grad`…) e a **paleta da matéria** (`[data-subject="azul|roxo|vermelho|grafite"]`).
No painel, o JS só troca `data-subject`; as cores vêm 100% do CSS. Para criar uma nova cor de matéria: adicione um bloco `[data-subject="x"]`, um `.swatch[data-k="x"]` e inclua a chave em `js/config.js` e no `@Pattern` de `MateriaRequest`.
A landing (index) usa cores fixas nos dois temas; só o fundo muda claro/escuro.

## Produção (mínimo)

- `SPRING_PROFILES_ACTIVE=prod` e variáveis de `.env.example` (o perfil `prod` não tem valores padrão: sem elas a app **não sobe**, de propósito). Sem o perfil `prod`, valem os valores públicos de dev, inclusive o token de recuperação exposto na tela.
- HTTPS no front e na API; `CORS_ORIGINS` só com o domínio do front; ajuste `API_BASE` e o `connect-src` da CSP (meta tag nos HTML).
- Envie os headers de segurança também no servidor do front (CSP, `frame-ancestors 'none'`, HSTS).
- Atrás de proxy: configure `server.forward-headers-strategy` para o rate limit enxergar o IP real. Mais de uma instância: troque o rate limit em memória por Redis.
- Faça backup do banco **e guarde a `STUDIY_ENCRYPTION_KEY` separada**: sem ela os textos cifrados não são recuperáveis. Teste a restauração.
- Preencha os `[PLACEHOLDERS]` de `privacidade.html` e `termos.html` e **revise com um profissional jurídico** antes de publicar.

## O que já está implementado (checklist)

| Área | Situação |
|---|---|
| Isolamento entre usuários | Toda rota filtra por dono (matéria → PDF/resumo/flashcard). **Corrigido:** geração aceitava o `pdfId` de outra conta (IDOR). |
| Senhas e sessões | bcrypt (custo 12); JWT de 2h com versão (logout e troca de senha revogam sessões); mensagens de erro genéricas |
| Recuperação de senha | Token aleatório de 256 bits, só o hash SHA-256 no banco, uso único, 30 min, nunca logado |
| Criptografia em repouso | AES-256-GCM em texto dos PDFs, resumos e flashcards (chave em variável de ambiente) |
| Validação | DTOs com limites; PDF validado por assinatura `%PDF-`, 10 MB, 300 páginas, nome sanitizado; arquivo original não é guardado |
| Abuso e custo | Rate limit (auth por IP; IA e upload por usuário) + cota mensal por plano + timeouts na IA |
| IA | Chave só no servidor; material do usuário tratado como dado não confiável no prompt; saída renderizada só como texto |
| API | CORS restrito, headers de segurança, erros sem stack trace, rotas negadas por padrão |
| LGPD | Aceite de termos registrado (data e versão), política e termos, exportar dados e excluir conta no painel |
| Testes | Criptografia, rate limit, hash de token, sanitização de nome |

## Ainda NÃO feito (próximos passos)

- Pagamento/assinatura (checkout no backend, webhook com assinatura validada, idempotência, estados da assinatura, `EntitlementService`). O botão Pro está "Em breve".
- E-mail real de recuperação de senha (hoje só em dev) e verificação de e-mail.
- Testes de isolamento com 2 contas (MockMvc + Testcontainers) e teste de contexto (`@Disabled` até haver banco de teste).
- Papel ADMIN/MFA, painel administrativo, logs de uso de IA por tokens/custo, retenção automática de logs.
- Token em `sessionStorage` é legível por XSS; a CSP e o `textContent` reduzem o risco. Alternativa futura: cookie HttpOnly + proteção CSRF.
- Fontes via Google Fonts expõem o IP ao Google; para evitar, hospede as fontes localmente.
=======
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
>>>>>>> 5e0a1b1 (feat(backend): segurança, perfil, pagamento Pro e limites de IA)
