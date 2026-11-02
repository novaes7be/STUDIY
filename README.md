# Studiy

Plataforma de estudos: organize matérias, envie PDFs e receba resumo + flashcards com revisão espaçada (SM-2). Plano grátis (20 gerações/mês) e Pro (300/mês, R$ 20 por 30 dias, cartão ou Pix via Mercado Pago).

```
frontend/            front estático (index, login, cadastro, dashboard, pagamento, termos, privacidade, recuperar-senha)
├── css/tokens.css   TODAS as cores (constantes). Edite aqui para mudar o visual.
└── js/              config, api, auth, dashboard, pagamento, theme, ui
backend/             API Spring Boot 3 (Java) + PostgreSQL + Flyway, Dockerfile
docker-compose.yml · .github/workflows/ci.yml · .env.example
```

## Rodar em desenvolvimento

1. PostgreSQL com o banco `studiy` (o Flyway aplica V1→V5 sozinho). Se vier de versões antigas, recrie o banco.
2. Backend (dev funciona sem configurar nada): `cd backend && mvn spring-boot:run` ou botão Run do IntelliJ (abra a pasta `backend`) → http://localhost:8080
3. Front: Live Server na pasta `frontend` (porta 5500). A API é definida em `frontend/js/config.js`.
4. Em dev, "Esqueci a senha" mostra o token na tela. Sem `ANTHROPIC_API_KEY`, a IA devolve respostas de teste.
5. Swagger (só dev): http://localhost:8080/swagger-ui.html (botão Authorize → cole o JWT do login).

Problemas de conexão: abra `http://localhost:8080/api/saude` (deve mostrar `{"status":"ok"}`). Se não abrir, o problema é o backend (Postgres parado, porta 8080 ocupada ou migração falhando em banco antigo).

## Pagamento (Mercado Pago)

- Criar a conta e o app em mercadopago.com.br/developers é gratuito, e o webhook também. A cobrança é só a taxa por venda aprovada.
- Dev: `studiy.pagamento.simulacao=true` faz o checkout virar um teste local, sem cobrança.
- Testar com Mercado Pago de verdade: `MP_ACCESS_TOKEN` de TESTE, túnel (ngrok) em `API_PUBLIC_URL`, e cadastre `{API_PUBLIC_URL}/api/pagamentos/webhook` no painel (evento "Pagamentos"); copie a assinatura secreta para `MP_WEBHOOK_SECRET`.
- O Pro só é liberado depois de webhook com `x-signature` válida e consulta direta à API do Mercado Pago; preço e duração são decididos no servidor.

## E-mail de recuperação de senha

Defina `SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD`, `MAIL_FROM` e `FRONT_URL` (ver `.env.example`). Sem SMTP nada é enviado (só um aviso no log). Em produção, `studiy.auth.expor-token-reset-dev=false` já vem desligado.

## Docker

```
cp .env.example .env   # preencha
docker compose up --build
```
O perfil padrão no compose é `prod` (exige todas as variáveis). Para testar localmente use `SPRING_PROFILES_ACTIVE=dev` no `.env`.

## Produção (mínimo)

- `SPRING_PROFILES_ACTIVE=prod` e variáveis de `.env.example` (sem elas a app não sobe, de propósito).
- HTTPS no front e na API; `CORS_ORIGINS` só com o domínio do front; ajuste `API_BASE` (`frontend/js/config.js`) e o `connect-src` da CSP (meta tag nos HTML).
- Envie headers de segurança também no servidor do front (CSP, `frame-ancestors 'none'`, HSTS).
- Atrás de proxy: `server.forward-headers-strategy` para o rate limit ver o IP real. Mais de uma instância: rate limit em Redis.
- Backup do banco e guarde a `STUDIY_ENCRYPTION_KEY` separada (sem ela os textos cifrados são irrecuperáveis).
- Preencha os `[PLACEHOLDERS]` de `privacidade.html` e revise termos/privacidade com um profissional jurídico.
- Se o Flyway reclamar de "description mismatch" na V4 num banco antigo, renomeie o arquivo para o nome já aplicado ou recrie o banco de dev.

## Cores (tokens)

`css/tokens.css`: primitivas (`--c-*`), semânticas claro/escuro e paleta da matéria (`[data-subject="azul|roxo|vermelho|grafite"]`). Nova cor de matéria: bloco `[data-subject="x"]`, um `.swatch[data-k="x"]`, a chave em `js/config.js` e no `@Pattern` de `MateriaRequest`.

## Segurança/LGPD implementadas

Isolamento por usuário (toda rota filtra pelo dono), bcrypt 12, JWT 2h com versão (logout/troca de senha revogam), reset de senha com token de 256 bits (só hash no banco, uso único, 30 min), AES-256-GCM em PDFs/resumos/flashcards, validação de PDF (assinatura, 10 MB, 300 páginas), rate limit (auth por IP; IA/upload por usuário) + cota mensal, CORS restrito, headers de segurança, erros sem stack trace, exportar dados e excluir conta, aceite de termos versionado.

## Ainda NÃO feito

- Teste do pagamento com credenciais reais do Mercado Pago.
- Verificação de e-mail no cadastro.
- Testes de isolamento com 2 contas (MockMvc + Testcontainers) e teste de contexto (`@Disabled` até haver banco de teste).
- Onboarding no primeiro acesso.
- Papel ADMIN/MFA, painel admin, log de tokens/custo de IA, retenção automática de logs.
- Token em `sessionStorage` é legível por XSS (CSP e `textContent` reduzem o risco); alternativa: cookie HttpOnly + CSRF.
- Google Fonts expõe o IP ao Google; hospede as fontes localmente.
