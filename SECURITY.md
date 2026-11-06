# Política de segurança

## Como reportar uma vulnerabilidade

**Não abra issue pública** para falhas de segurança.

Use o reporte privado do GitHub: aba **Security → Report a vulnerability** deste repositório
(GitHub Private Vulnerability Reporting). Só os mantenedores veem o relato.

Inclua, se puder:

- o que é afetado (rota da API, tela, arquivo) e o impacto (ex.: ler dados de outra conta);
- passos para reproduzir ou uma prova de conceito mínima;
- versão/commit testado.

## O que esperar

- Confirmação de recebimento em até **3 dias úteis**.
- Avaliação inicial e severidade em até **10 dias úteis**.
- Correção priorizada por severidade; você é avisado antes da divulgação e creditado (se quiser).

## Escopo

Dentro: o código deste repositório (backend em `backend/`, front em `frontend/`, deploy em `deploy/`).

Fora: ataques de negação de serviço volumétricos, engenharia social, e falhas em serviços de terceiros
(Mercado Pago, Anthropic, provedor de e-mail) — reporte a eles diretamente.

## Teste com responsabilidade

- Use **apenas contas suas** e ambiente local (`docker compose` ou perfil `dev`).
- Não acesse, altere nem apague dados de outras pessoas; pare e reporte assim que comprovar o problema.
- Não rode scanners automatizados contra instâncias de produção.
