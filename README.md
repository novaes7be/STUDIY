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
