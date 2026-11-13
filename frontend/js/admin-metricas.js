/* Visão geral do painel admin: blocos de números e gráficos em SVG desenhados à mão
   (a CSP só permite script próprio, então nada de biblioteca de gráfico por CDN).
   Todo texto entra por textContent. Uma cor só (accent): cada gráfico tem uma série. */
(function () {
  'use strict';
  var SVG = 'http://www.w3.org/2000/svg';
  var brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
  var num = new Intl.NumberFormat('pt-BR');
  var pct = new Intl.NumberFormat('pt-BR', { style: 'percent', maximumFractionDigits: 1 });
  var diaCurto = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', timeZone: 'UTC' });
  var diaLongo = new Intl.DateTimeFormat('pt-BR', { weekday: 'short', day: '2-digit', month: 'short', timeZone: 'UTC' });

  var ROTULOS = {
    PENDENTE: 'Pendente', PAGO: 'Pago', REJEITADO: 'Rejeitado', REEMBOLSADO: 'Reembolsado',
    PIX: 'Pix', CARTAO: 'Cartão', RESUMO: 'Resumos', FLASHCARD: 'Flashcards', SIMULADO: 'Simulados'
  };

  function el(tag, cls, texto) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (texto !== undefined && texto !== null) e.textContent = texto;
    return e;
  }
  function s(tag, attrs) {
    var e = document.createElementNS(SVG, tag);
    Object.keys(attrs || {}).forEach(function (k) { e.setAttribute(k, attrs[k]); });
    return e;
  }
  function n(v) { return v === null || v === undefined ? '—' : num.format(v); }
  function proporcao(parte, todo) { return todo ? pct.format(parte / todo) : '—'; }

  // ---- tooltip compartilhado ----
  var tip;
  function mostrarTip(ev, titulo, valor) {
    tip = tip || document.getElementById('tooltip');
    while (tip.firstChild) tip.removeChild(tip.firstChild);
    tip.append(el('span', 'admin-tooltip-titulo', titulo), el('strong', null, valor));
    tip.hidden = false;
    var x = Math.min(ev.clientX + 12, window.innerWidth - tip.offsetWidth - 8);
    var y = ev.clientY - tip.offsetHeight - 12;
    tip.style.left = x + 'px';
    tip.style.top = (y < 8 ? ev.clientY + 16 : y) + 'px';
  }
  function esconderTip() { if (tip) tip.hidden = true; }

  // ---- blocos ----
  function tile(rotulo, valor, detalhe, destaque) {
    var t = el('div', 'admin-tile' + (destaque ? ' admin-tile-destaque' : ''));
    t.append(el('span', 'admin-tile-rotulo', rotulo), el('strong', 'admin-tile-valor', valor));
    if (detalhe) t.append(el('span', 'admin-tile-detalhe', detalhe));
    return t;
  }

  /** Colunas diárias com ponta arredondada só em cima, apoiadas na linha de base. */
  function colunas(titulo, pontos, formatar) {
    var fig = el('figure', 'admin-grafico');
    var total = pontos.reduce(function (a, p) { return a + p.valor; }, 0);
    var cab = el('figcaption', 'admin-grafico-cab');
    cab.append(el('span', null, titulo), el('strong', null, formatar(total)));
    fig.append(cab);

    var L = 640, A = 160, base = A - 20, topo = 12;
    var max = Math.max.apply(null, pontos.map(function (p) { return p.valor; }).concat([1]));
    var passo = L / pontos.length;
    var larg = Math.max(1, Math.min(18, passo - 2));
    var svg = s('svg', { viewBox: '0 0 ' + L + ' ' + A, class: 'admin-grafico-svg', role: 'img',
      'aria-label': titulo + ': ' + formatar(total) + ' no período, máximo de ' + formatar(max) + ' em um dia' });

    // grade: só a linha de base e a do máximo, recessivas
    svg.append(s('line', { x1: 0, x2: L, y1: base, y2: base, class: 'admin-eixo' }));
    svg.append(s('line', { x1: 0, x2: L, y1: topo, y2: topo, class: 'admin-grade' }));
    var rMax = s('text', { x: 0, y: topo - 2, class: 'admin-eixo-texto' }); rMax.textContent = formatar(max);
    svg.append(rMax);

    pontos.forEach(function (p, i) {
      var x = i * passo + (passo - larg) / 2;
      var h = p.valor ? Math.max(2, (p.valor / max) * (base - topo)) : 0;
      var r = Math.min(4, larg / 2, h);
      if (h) {
        svg.append(s('path', { class: 'admin-barra', d:
          'M' + x + ',' + base + 'V' + (base - h + r) + 'Q' + x + ',' + (base - h) + ' ' + (x + r) + ',' + (base - h) +
          'H' + (x + larg - r) + 'Q' + (x + larg) + ',' + (base - h) + ' ' + (x + larg) + ',' + (base - h + r) + 'V' + base + 'Z' }));
      }
      // alvo de hover maior que a barra: a coluna inteira
      var alvo = s('rect', { x: i * passo, y: 0, width: passo, height: A, class: 'admin-alvo' });
      var data = new Date(p.dia + 'T00:00:00Z');
      alvo.addEventListener('mousemove', function (ev) { mostrarTip(ev, diaLongo.format(data), formatar(p.valor)); });
      alvo.addEventListener('mouseleave', esconderTip);
      svg.append(alvo);
    });
    // rótulos do eixo x: primeiro, meio e último dia
    [0, Math.floor(pontos.length / 2), pontos.length - 1].forEach(function (i, k) {
      if (!pontos[i] || (k === 1 && pontos.length < 5)) return;
      var t = s('text', { x: i * passo + passo / 2, y: A - 4, class: 'admin-eixo-texto',
        'text-anchor': k === 0 ? 'start' : k === 2 ? 'end' : 'middle' });
      t.textContent = diaCurto.format(new Date(pontos[i].dia + 'T00:00:00Z'));
      svg.append(t);
    });
    fig.append(svg);
    return fig;
  }

  /** Distribuição de uma variável categórica: barras horizontais, todas na mesma cor, rótulo direto. */
  function distribuicao(titulo, mapa) {
    var fig = el('figure', 'admin-grafico');
    fig.append(el('figcaption', 'admin-grafico-cab', titulo));
    var chaves = Object.keys(mapa || {});
    if (!chaves.length) { fig.append(el('p', 'hint', 'Nada no período.')); return fig; }
    var total = chaves.reduce(function (a, k) { return a + mapa[k]; }, 0);
    var max = Math.max.apply(null, chaves.map(function (k) { return mapa[k]; }));
    var lista = el('ul', 'admin-dist');
    chaves.sort(function (a, b) { return mapa[b] - mapa[a]; }).forEach(function (k) {
      var li = el('li');
      var trilho = el('span', 'admin-dist-trilho');
      var barra = el('span', 'admin-dist-barra');
      barra.style.width = Math.max(2, (mapa[k] / max) * 100) + '%';
      trilho.append(barra);
      li.append(el('span', 'admin-dist-rotulo', ROTULOS[k] || k), trilho,
        el('span', 'admin-dist-valor', n(mapa[k]) + ' · ' + proporcao(mapa[k], total)));
      lista.append(li);
    });
    fig.append(lista);
    return fig;
  }

  function secao(titulo, tiles, graficos) {
    var sec = el('section', 'admin-secao');
    sec.append(el('h2', null, titulo));
    var grade = el('div', 'admin-tiles');
    tiles.forEach(function (t) { grade.append(t); });
    sec.append(grade);
    if (graficos && graficos.length) {
      var g = el('div', 'admin-graficos');
      graficos.forEach(function (x) { g.append(x); });
      sec.append(g);
    }
    return sec;
  }

  function contagem(c) { return n(c.periodo) + ' no período'; }
  function centavos(v) { return brl.format(v / 100); }
  function inteiro(v) { return num.format(v); }

  function render(alvo, m) {
    while (alvo.firstChild) alvo.removeChild(alvo.firstChild);
    var u = m.usuarios, v = m.vendas, c = m.conteudo, ia = m.ia;

    alvo.append(secao('Vendas', [
      tile('Receita no período', v.receitaPeriodo, 'Total histórico: ' + v.receitaTotal, true),
      tile('Vendas pagas', n(v.pedidosPagos)),
      tile('Ticket médio', v.ticketMedio || '—'),
      tile('Conversão', v.taxaConversao === null || v.taxaConversao === undefined ? '—' : pct.format(v.taxaConversao), 'pedidos criados que viraram venda'),
      tile('Reembolsos', n(v.reembolsos))
    ], [
      colunas('Receita por dia', v.receitaPorDia, centavos),
      distribuicao('Pedidos por status', v.pedidosPorStatus),
      distribuicao('Vendas por método', v.pagosPorMetodo)
    ]));

    alvo.append(secao('Usuários', [
      tile('Usuários', n(u.total), n(u.novos) + ' novos no período', true),
      tile('Premium ativos', n(u.premiumAtivos), proporcao(u.premiumAtivos, u.total) + ' da base'),
      tile('Ativos (7 dias)', n(u.ativos7d), proporcao(u.ativos7d, u.total) + ' da base'),
      tile('Ativos (30 dias)', n(u.ativos30d), proporcao(u.ativos30d, u.total) + ' da base'),
      tile('E-mail verificado', proporcao(u.verificados, u.total), n(u.verificados) + ' usuários')
    ], [colunas('Cadastros por dia', u.cadastrosPorDia, inteiro)]));

    alvo.append(secao('Conteúdo gerado', [
      tile('Resumos', n(c.resumos.total), contagem(c.resumos), true),
      tile('Simulados', n(c.simulados.total), contagem(c.simulados)),
      tile('Flashcards', n(c.flashcards.total), contagem(c.flashcards)),
      tile('PDFs enviados', n(c.pdfs.total), contagem(c.pdfs)),
      tile('Matérias', n(c.materias)),
      tile('Revisões feitas', n(c.revisoesPeriodo), 'no período'),
      tile('Nota média nos simulados', c.notaMediaSimulados === null ? '—' : String(c.notaMediaSimulados).replace('.', ','), 'no período')
    ], [
      colunas('Resumos por dia', c.resumosPorDia, inteiro),
      colunas('Simulados por dia', c.simuladosPorDia, inteiro),
      colunas('Flashcards por dia', c.flashcardsPorDia, inteiro)
    ]));

    alvo.append(secao('Inteligência artificial', [
      tile('Custo no período', ia.custo, null, true),
      tile('Chamadas', n(ia.chamadas)),
      tile('Falhas', n(ia.falhas), proporcao(ia.falhas, ia.chamadas) + ' das chamadas')
    ], [
      colunas('Chamadas por dia', ia.chamadasPorDia, inteiro),
      distribuicao('Chamadas por tipo', ia.chamadasPorTipo)
    ]));

    alvo.append(el('p', 'hint', 'Atualizado em ' + new Date(m.geradoEm).toLocaleString('pt-BR')
      + '. Revisões e IA guardam histórico limitado pela política de retenção.'));
  }

  window.adminMetricas = { render: render };
})();
