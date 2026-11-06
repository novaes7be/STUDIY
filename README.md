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

## Deploy (Docker + HTTPS)

```
cp .env.example .env   # preencha (DB_*, JWT_SECRET, STUDIY_ENCRYPTION_KEY, DOMAIN, FRONT_URL, CORS_ORIGINS, MP_*, SMTP)
docker compose up -d --build
```

- `db` (Postgres), `api` (Spring Boot, só na rede interna) e `web` (Caddy: serve o `frontend/`, encaminha `/api` para a API e emite o HTTPS sozinho).
- Aponte o DNS do seu domínio para o IP do servidor e abra as portas 80 e 443. Com `DOMAIN=localhost` você testa tudo localmente (certificado de teste).
- Front e API ficam na mesma origem, então não há CORS nem ajuste de CSP. O `API_PRODUCAO` do `js/config.js` já está vazio (mesma origem).
- Perfil padrão do compose é `prod` (exige todas as variáveis). Para testar com padrões de dev use `SPRING_PROFILES_ACTIVE=dev` no `.env`.
- Hospedagem: qualquer VPS com Docker (Hetzner, DigitalOcean, Contabo etc.). Backup do volume `pgdata` e guarde a `STUDIY_ENCRYPTION_KEY` fora do servidor.

## E-mail (recuperação de senha e confirmação de cadastro)

Qualquer SMTP serve: Brevo, Resend, Mailgun ou Gmail com senha de app (os três primeiros têm plano gratuito). Preencha `SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD` e `MAIL_FROM` no `.env`. Com `EXIGIR_VERIFICACAO_EMAIL=true`, o cadastro envia um link (vale 24 h, uso único) e o login só funciona depois da confirmação; contas antigas já contam como confirmadas. Sem SMTP, deixe `false`.

## Testes

`mvn test` roda os testes unitários e o `IsolamentoUsuariosTest` (duas contas, uma não vê nem altera dados da outra). Esse usa Postgres em container (Testcontainers) e é pulado sozinho se o Docker não estiver rodando. O CI roda tudo no GitHub.

## Cores (tokens)

Paleta: roxo, rosa e azul pastéis, em `frontend/css/tokens.css` (primitivas `--c-*`, semânticas claro/escuro e paleta da matéria). Cada cor tem um papel só:

- Roxo: ação principal (botões, links, foco, barras) e seções de destaque. Lilás: fundos suaves (hero, chamada final).
- Rosa: só detalhes pequenos (selo, destaque pontual). Azul pastel: só avisos e dicas. Vermelho: só excluir e erros.
- Botões: `.btn-grad` primário, `.btn-ghost` secundário, `.btn-light` primário sobre fundo escuro, `.btn-dark` menu, `.btn-danger` excluir.
- Cores de matéria: as chaves `azul|roxo|vermelho|grafite` ficam no banco, então não mudam; "vermelho" agora exibe o Rosa. Para criar uma nova, adicione um bloco `[data-subject="x"]`, um `.swatch[data-k="x"]`, a chave em `js/config.js` e no `@Pattern` de `MateriaRequest`.

## Segurança/LGPD implementadas

Isolamento por usuário (toda rota filtra pelo dono), bcrypt 12, JWT 2h com versão (logout/troca de senha revogam), reset de senha com token de 256 bits (só hash no banco, uso único, 30 min), AES-256-GCM em PDFs/resumos/flashcards, validação de PDF (assinatura, 10 MB, 300 páginas), rate limit (auth por IP; IA/upload por usuário) + cota mensal, CORS restrito, headers de segurança, erros sem stack trace, exportar dados e excluir conta, aceite de termos versionado.

## Ainda NÃO feito

- Teste do pagamento com credenciais reais do Mercado Pago.
- Preencher os `[PLACEHOLDERS]` de `privacidade.html` e `termos.html` e revisar com um profissional jurídico.
- Contratar o SMTP e (opcional) ligar `EXIGIR_VERIFICACAO_EMAIL`.
- Mais testes de isolamento (resumos, flashcards, PDFs, pedidos) e teste de contexto (`@Disabled`).
- Papel ADMIN/MFA, painel admin, log de tokens/custo de IA, retenção automática de logs.
- Token em `sessionStorage` é legível por XSS (CSP e `textContent` reduzem o risco); alternativa: cookie HttpOnly + CSRF.
- Google Fonts expõe o IP ao Google; hospede as fontes localmente.
