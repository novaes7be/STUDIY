# Auditoria de design — Studiy (front web)

Data: 2026-11-06 · Referência: Apple Human Interface Guidelines (design principles, accessibility, typography,
color, layout, buttons, modality, feedback, motion, dark mode, branding) aplicadas como princípios a um
app web. Ambiente: `docker-compose.dev.yml`, telas em 390, 1440 px (claro e escuro), contraste calculado
a partir dos hex do CSS (WCAG 2.x).

## Resumo

**Avaliação: Precisa de trabalho.** A base é boa (tokens centralizados, `textContent`, foco visível,
`prefers-reduced-motion`, dark mode completo), mas o produto **não tinha tese visual**: roxo pastel +
gradientes + cartões girados é o visual padrão de "app SaaS gerado". A ousadia estava espalhada (três
cartões de status coloridos, painel em gradiente, botões em gradiente, brilho que segue o cursor) em vez
de concentrada no que o aluno veio fazer: **revisar hoje**.

**Tese adotada:** *estudo como cultivo* (paleta Forest & Sage). Sequência de dias, nível e revisão
espaçada são crescimento; o verde carrega isso sem precisar de ilustração. **Elemento-assinatura:** o
cartão "Revisar agora" é o único bloco cheio de cor no painel; todo o resto fica quieto.

## Problemas encontrados

| # | Problema | Local | Sev. | Impacto | Solução | Referência |
|---|---|---|---|---|---|---|
| 1 | Borda dos campos com **1.37:1** (claro) e **1.48:1** (escuro); WCAG 1.4.11 pede 3:1 | Todos os formulários | **Crítica** | Campo quase invisível para baixa visão; não se sabe onde clicar | Borda de controle `#7a978c` (3.17:1) / `#5d7d71` (3.41:1) | `accessibility.md › Color and effects` ("strive to meet color contrast minimum standards") |
| 2 | No celular o painel começa **480 px abaixo** do topo: a barra lateral inteira (logo, home, matérias, perfil, uso) vem antes do conteúdo | Painel, < 900 px | **Crítica** | Em 844 px de altura, o aluno vê só menu; a tarefa do dia fica fora da tela | Barra lateral vira cabeçalho compacto: matérias em trilho horizontal, perfil em avatar, uso numa linha | `layout.md › Best practices` ("order content by relative importance") |
| 3 | Botões mudam o próprio **padding no hover** e trocam o ícone por uma seta | Todos os `.btn` | Alta | Mudança de layout a cada hover; o ícone perde o significado | Ícone fixo; hover = cor; pressionado = escala 0,97 com mola curta | `buttons.md › Best practices` ("always include a press state"); `motion.md` ("avoid adding motion to UI interactions that occur frequently") |
| 4 | Botão sem ícone definido herda o **✦ de IA**: "Cancelar", "Fechar", "Reenviar link" mostram brilho | Diálogos, aviso de e-mail | Alta | Ícone mente sobre a ação (✦ = gerar com IA) | Sem ícone por padrão; ✦ só em ações de IA | `buttons.md › Best practices` ("associate familiar actions with familiar icons") |
| 5 | Classe `.who` da landing (grid de 3 colunas) vaza para o chip de perfil | Painel, barra lateral | Alta | Nome cortado ("Te…") e "Editar perfil" espremido | Classe própria `.profile-who` | Judgment (bug de CSS) |
| 6 | Alvos de toque abaixo de 44 px: amostras de cor 36, botão de tema 40, `.btn-sm` ~33 | Painel, auth | Alta | Erro de toque no celular | `min-height: 44px` em controles; amostras 44 px no toque | `accessibility.md › Mobility` (44x44 pt padrão) |
| 7 | Vermelho usado como **decoração** no selo "4 dias seguidos" da landing, contrariando a regra do próprio tokens.css (vermelho = erro) | Landing, hero | Média | Lê como alerta | Selo com cor de ação | `color.md › Best practices` ("use color consistently") |
| 8 | Texto secundário **4.25:1** sobre lilás e links **4.12:1** sobre fundo tingido; matérias Azul/Rosa com link 4.29 / 4.14 | Landing, painel | Média | Leitura difícil em texto pequeno | Novos tons: muted `#4d665d` 5.55–5.89:1; ação `#3c6f5b` 4.82–5.81:1 | `accessibility.md › Color and effects` (4.5:1 até 17 pt) |
| 9 | Cinco gradientes diferentes (fundo da página, hero, faixa, botões, cartões) e sombras roxas fortes | Global | Média | Ruído visual; nada se destaca porque tudo se destaca | Superfícies chapadas; uma sombra suave em 2 níveis; gradiente nenhum | `branding.md › Best practices` ("apply your app's accent color judiciously") |
| 10 | Três cartões de status com cores cheias + painel em gradiente competem com a ação principal | Painel | Média | Não fica claro o que fazer agora | Status em superfícies neutras; só "Revisar agora" em cor | `buttons.md` ("keep the number of prominent buttons to one or two per view"); `branding.md` |
| 11 | Botão de tema no cabeçalho do painel, ao lado da ação principal | Painel | Média | Controle de preferência compete com "Enviar PDF" | Tema segue o sistema por padrão; botão vai para a barra lateral | `dark-mode.md › Best practices` ("avoid offering an app-specific appearance setting") — mantido por não remover recurso |
| 12 | Diálogos e toasts aparecem sem transição; o toast de erro não tem ícone (só cor) | Painel | Média | Mudança abrupta; erro identificado só pela cor | Entrada curta (opacidade + escala 0,96 → 1, 220 ms); erro com ícone e `role="alert"` | `motion.md` ("brevity and precision"); `accessibility.md` ("more than color alone") |
| 13 | Conteúdo do painel sem largura máxima | Painel, ≥ 1440 px | Média | Em 1920 px as linhas de texto ficam longas e os cartões esticam | `max-width: 1180px` no conteúdo | `layout.md › Best practices` |
| 14 | Título da matéria em 52 px colorido + "Bem-vindo ao Studiy" duplicado com o título do cartão de boas-vindas | Painel | Baixa | Hierarquia gritando; repetição | Título 34 px na cor do texto; cartão sem repetir | `typography.md › Best practices` (hierarquia por peso e tamanho) |
| 15 | "Voltar para a home" como botão-pílula com contorno em toda tela de auth, acima do logo | Auth | Baixa | Compete com o botão primário; logo repetido | Link de texto discreto | `branding.md` ("resist the temptation to display your logo throughout") |
| 16 | Estado de erro dos campos inexistente (só a mensagem no topo) | Formulários | Baixa | Não se sabe qual campo corrigir | Borda e texto de erro no campo via `aria-invalid` | Lens 4 (validação dinâmica) |

## Top 10 que mais prejudicam a qualidade

1. Painel no celular escondido atrás do menu (#2)
2. Campos sem borda perceptível (#1)
3. Botões que mudam de tamanho no hover (#3)
4. Falta de tese visual / ousadia espalhada (#9, #10)
5. Ícone de IA em "Cancelar" (#4)
6. Nome cortado no perfil (#5)
7. Alvos de toque pequenos (#6)
8. Contraste de texto secundário e links (#8)
9. Diálogos sem transição e erro só por cor (#12)
10. Conteúdo sem largura máxima no desktop largo (#13)

## Componentes reconstruídos

Botão (todas as variantes), campo de texto, cartão/painel, cartões de status, diálogo, toast, barra
lateral (desktop) e cabeçalho compacto (celular), amostras de cor, selo da landing.

## Inconsistências entre páginas

- Raios: 12, 14, 16, 18, 20, 24, 28, 32 px → passa a ser **3 raios** (10 controles internos, 16 cartões, 24 seções).
- Bordas: 1 px em uns cartões, 2 px em outros → **1 px** em todos.
- Sombras: 6 receitas diferentes → **2 níveis** (`--shadow-1`, `--shadow-2`).
- Pagamento, admin e legais usavam o cartão "auth" com variações próprias → mesmo cartão base.

## Notas (0–10)

| Critério | Antes | Depois (meta verificada no reteste) |
|---|---|---|
| Visual Design | 5 | 8 |
| UI Consistency | 5 | 8 |
| UX | 5 | 8 |
| Accessibility | 4 | 8 |
| Responsiveness | 4 | 8 |
| Microinteractions | 4 | 7 |
| Overall Polish | 4.5 | 8 |

Critério das notas: 9–10 exigiria testes com usuários reais, suporte a aumento de texto a 200% verificado
em todas as telas e ícones desenhados para o produto; nada disso foi feito.
