# Design do Studiy — editorial suíço com movimento

Capturas do redesign do frontend. As telas do painel, flashcard, perfil e pagamento usam **dados de exemplo** (API simulada só para a captura); nada aqui vem de contas reais. Capturadas com "reduzir movimento" ligado, por isso os títulos aparecem já resolvidos (sem o efeito de embaralhar).

## Telas

| | |
|---|---|
| ![Landing, tema claro](telas/01-landing-claro.png) | ![Landing, tema escuro](telas/02-landing-escuro.png) |
| Landing (claro) | Landing (escuro) |
| ![Login](telas/04-login-claro.png) | ![Painel, claro](telas/06-painel-claro.png) |
| Login (split) | Painel (claro) |
| ![Painel, escuro](telas/07-painel-escuro.png) | ![Flashcard virado](telas/09-flashcard.png) |
| Painel (escuro) | Revisão com flashcard virado |
| ![Perfil](telas/10-perfil.png) | ![Pagamento](telas/11-pagamento.png) |
| Perfil (nome gigante) | Assinatura do Pro |
| ![Política de Privacidade, escuro](telas/12-privacidade-escuro.png) | |
| Páginas legais (escuro) | |

Celular (390 px):

| | | |
|---|---|---|
| ![Landing no celular](telas/03-landing-celular.png) | ![Cadastro no celular, escuro](telas/05-cadastro-celular-escuro.png) | ![Painel no celular](telas/08-painel-celular.png) |
| Landing | Cadastro (escuro) | Painel |

## Código mais relevante

1. **Tokens semânticos** — uma cor = um papel; o tema escuro só redefine os papéis. [`frontend/css/tokens.css`](../../frontend/css/tokens.css)
   ![Tokens](codigo/c1-tokens.png)
2. **Variantes de botão e estado carregando** — cada variante define 5 variáveis privadas; hover, foco e spinner ficam no `.btn` base. [`frontend/css/buttons.css`](../../frontend/css/buttons.css)
   ![Botões](codigo/c2-botoes.png)
3. **`data-scramble`** — percorre os nós de texto (preserva `<br>`/`<span>`, nunca usa `innerHTML`) e expõe o texto final a leitores de tela. [`frontend/js/motion.js`](../../frontend/js/motion.js)
   ![Scramble](codigo/c3-scramble.png)
4. **Flashcard com flip 3D** — frente e verso trocam `aria-hidden` e o foco vai para a primeira nota. [`frontend/js/dashboard.js`](../../frontend/js/dashboard.js)
   ![Flashcard](codigo/c4-flashcard.png)
5. **Flip 3D em CSS** — faces empilhadas em grid (a altura acompanha o lado maior) e troca por opacidade com movimento reduzido. [`frontend/css/motion.css`](../../frontend/css/motion.css)
   ![Flip](codigo/c5-flip.png)

As imagens de código mostram trechos exatos dos arquivos, com o caminho e as linhas no topo de cada janela.
