/* Painel do Studiy. Todo conteúdo vindo da API (nomes, resumos, flashcards gerados por IA)
   é inserido com textContent/createTextNode, NUNCA como HTML (proteção contra XSS). */
(function () {
  'use strict';
  var CFG = window.STUDIY;
  var state = { perfil: null, materias: [], atual: null, pdfs: [], resumos: [], tela: 'materia', rankingTipo: 'SEMANA' };

  var $ = function (sel) { return document.querySelector(sel); };
  var app = $('#app');

  function h(tag, attrs) {
    var el = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (k) {
      var v = attrs[k];
      if (v === null || v === undefined || v === false) return;
      if (k === 'class') el.className = v;
      else if (k === 'text') el.textContent = v;
      else if (k.indexOf('on') === 0) el.addEventListener(k.slice(2), v);
      else el.setAttribute(k, v === true ? '' : v);
    });
    for (var i = 2; i < arguments.length; i++) {
      var kid = arguments[i];
      if (kid === null || kid === undefined || kid === false) continue;
      el.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
    }
    return el;
  }
  function limpar(el) { while (el.firstChild) el.removeChild(el.firstChild); }

  function toast(text, erro) {
    var t = h('div', { class: 'toast' + (erro ? ' error' : ''), text: text });
    $('#toasts').append(t);
    setTimeout(function () { t.remove(); }, 4500);
  }
  function falha(e) { toast((e && e.message) || 'Algo deu errado.', true); }
  function chaveCor(cor) {
    return CFG.SUBJECTS.some(function (s) { return s.key === cor; }) ? cor : CFG.DEFAULT_SUBJECT;
  }
  function fmtData(iso) {
    var d = new Date(iso);
    return isNaN(d) ? '' : d.toLocaleDateString('pt-BR');
  }

  /* ---------- Carregamento ---------- */
  async function init() {
    try {
      var r = await Promise.all([api.get('/api/usuarios/me'), api.get('/api/materias')]);
      state.perfil = r[0]; state.materias = r[1];
      api.marcarLogado();
    } catch (e) { falha(e); return; }
    renderUso();
    renderLateral();
    await selecionar(state.materias.length ? state.materias[0].id : null);
    if (state.perfil.onboardingPendente && window.studiyOnboarding) {
      window.studiyOnboarding(function () {
        api.post('/api/usuarios/me/onboarding').then(function (p) { state.perfil = p; }, function () { /* tenta de novo no próximo acesso */ });
      });
    }
  }

  async function recarregarPerfil() {
    try { state.perfil = await api.get('/api/usuarios/me'); renderUso(); } catch (e) { /* mantém o anterior */ }
  }
  async function recarregarMaterias() {
    state.materias = await api.get('/api/materias');
    renderLateral();
    state.atual = state.materias.find(function (m) { return state.atual && m.id === state.atual.id; }) || null;
  }

  async function selecionar(id) {
    sairDoRanking();
    state.atual = state.materias.find(function (m) { return m.id === id; }) || null;
    state.pdfs = []; state.resumos = [];
    if (state.atual) {
      try {
        var r = await Promise.all([
          api.get('/api/materias/' + id + '/pdfs'),
          api.get('/api/materias/' + id + '/resumos')
        ]);
        state.pdfs = r[0]; state.resumos = r[1];
      } catch (e) { falha(e); }
    }
    renderLateral();
    renderPrincipal();
  }

  /* ---------- Renderização ---------- */
  function renderLateral() {
    var box = $('#subjectList');
    limpar(box);
    state.materias.forEach(function (m) {
      box.append(h('button', {
        class: 'subj', type: 'button', text: m.nome, 'data-mark': chaveCor(m.cor),
        'aria-current': state.tela === 'materia' && state.atual && state.atual.id === m.id ? 'true' : 'false',
        onclick: function () { selecionar(m.id); }
      }));
    });
    box.append(h('button', { class: 'subj add', type: 'button', text: '+ Nova matéria', onclick: abrirNovaMateria }));
    $('#rankingBtn').setAttribute('aria-current', state.tela === 'ranking' ? 'true' : 'false');
  }

  /* ---------- Ranking ----------
     A API só lê uma "foto" pré-calculada no servidor. Aqui: mostra na hora a última foto
     salva no navegador (prévia), busca a nova em segundo plano e troca sem spinner; com a
     tela aberta, confere de novo a cada minuto. Só exibe o que vem pronto da API. */
  var PREVIA = 'studiy-previa-ranking-';
  var rankingTimer = null;

  function esquecerPreviasDoRanking() {
    ['SEMANA', 'SEQUENCIA', 'XP'].forEach(function (t) { try { localStorage.removeItem(PREVIA + t); } catch (e) { /* ignora */ } });
  }
  function lerPrevia(tipo) {
    try { return JSON.parse(localStorage.getItem(PREVIA + tipo) || 'null'); } catch (e) { return null; }
  }
  function salvarPrevia(tipo, dados) {
    try { localStorage.setItem(PREVIA + tipo, JSON.stringify(dados)); } catch (e) { /* storage cheio ou bloqueado */ }
  }
  function haQuanto(iso) {
    var min = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000));
    return min < 1 ? 'agora há pouco' : min === 1 ? 'há 1 minuto' : 'há ' + min + ' minutos';
  }

  function abrirRanking() {
    state.tela = 'ranking';
    renderLateral();
    $('#uploadBtn').hidden = true;
    $('#subjectTitle').textContent = 'Ranking';
    document.title = 'Ranking — Studiy';
    carregarRanking(state.rankingTipo);
    clearInterval(rankingTimer);
    rankingTimer = setInterval(function () { if (!document.hidden) carregarRanking(state.rankingTipo, true); }, 60000);
  }
  function sairDoRanking() {
    state.tela = 'materia';
    clearInterval(rankingTimer);
    rankingTimer = null;
  }

  async function carregarRanking(tipo, silencioso) {
    state.rankingTipo = tipo;
    var previa = lerPrevia(tipo);
    if (!silencioso) renderRanking(previa, tipo, false);       // prévia na hora (ou esqueleto)
    try {
      var novo = await api.get('/api/ranking?tipo=' + encodeURIComponent(tipo));
      if (state.tela !== 'ranking' || state.rankingTipo !== tipo) return;  // usuário já saiu ou trocou de aba
      salvarPrevia(tipo, novo);
      if (!previa || JSON.stringify(previa) !== JSON.stringify(novo) || !silencioso) renderRanking(novo, tipo, false);
      else if (novo.geradoEm) $('.rank-meta span').textContent = ' · atualizado ' + haQuanto(novo.geradoEm);  // só o relógio
    } catch (e) {
      if (state.tela === 'ranking' && state.rankingTipo === tipo && !silencioso) renderRanking(previa, tipo, true);
    }
  }

  function renderRanking(d, tipo, offline) {
    var content = $('#content');
    limpar(content);
    var abas = h('div', { class: 'rank-tabs', role: 'group', 'aria-label': 'Quadro do ranking' });
    [['SEMANA', 'Semana'], ['SEQUENCIA', 'Sequência'], ['XP', 'XP']].forEach(function (t) {
      abas.append(h('button', {
        class: 'btn btn-tertiary btn-sm', type: 'button', text: t[1], 'aria-pressed': t[0] === tipo ? 'true' : 'false',
        onclick: function () { if (t[0] !== state.rankingTipo) carregarRanking(t[0]); }
      }));
    });
    content.append(abas);

    if (!d) {  // primeira vez, sem prévia: esqueleto em vez de spinner
      var esq = h('ol', { class: 'rank-list is-loading', 'aria-busy': 'true', 'aria-label': 'Carregando ranking' });
      for (var i = 0; i < 6; i++) esq.append(h('li', { class: 'rank-row rank-skel' }));
      content.append(offline ? h('p', { class: 'msg error', role: 'alert', text: 'Não foi possível carregar o ranking agora.' }) : esq);
      return;
    }

    content.append(h('p', { class: 'rank-meta' },
      h('strong', { text: d.titulo }),
      h('span', { text: d.geradoEm ? ' · atualizado ' + haQuanto(d.geradoEm) : ' · sendo calculado' }),
      offline ? h('span', { class: 'rank-offline', text: ' · sem conexão, mostrando a última versão' }) : ''));

    if (!d.participando) {
      content.append(h('section', { class: 'panel rank-cta' },
        h('h2', { text: 'Entre no ranking' }),
        h('p', { class: 'hint-text', text: 'Escolha um apelido no seu perfil. Só o apelido aparece para os outros; seu nome e e-mail nunca.' }),
        h('button', { class: 'btn btn-accent btn-sm btn-arrow', type: 'button', text: 'Escolher apelido',
          onclick: function () { $('#accountBtn').click(); } })));
    } else if (d.voce) {
      content.append(h('section', { class: 'rank-me' },
        h('p', { class: 'card-label', text: 'Sua posição' }),
        h('p', { class: 'rank-me-pos' }, h('span', { text: d.voce.posicao }), h('small', { text: 'º' })),
        h('p', { class: 'rank-me-val', text: d.voce.valor + ' ' + d.unidade })));
    } else {
      content.append(h('p', { class: 'hint-text', text: 'Você ainda não pontuou neste quadro. Revise alguns cartões e volte aqui.' }));
    }

    if (!d.itens.length) {
      content.append(h('p', { class: 'empty', text: 'Ninguém pontuou neste quadro ainda.' }));
    } else {
      var lista = h('ol', { class: 'rank-list' });
      d.itens.forEach(function (it) {
        lista.append(h('li', { class: 'rank-row' + (it.voce ? ' is-voce' : '') + (it.posicao <= 3 ? ' is-top' : '') },
          h('span', { class: 'rank-pos', text: it.posicao + 'º' }),
          h('span', { class: 'rank-nome', text: it.apelido + (it.voce ? ' (você)' : '') }),
          h('span', { class: 'rank-val', text: it.valor + ' ' + d.unidade })));
      });
      content.append(lista);
    }
    if (window.Motion) Motion.observar(content);
  }

  function renderUso() {
    var p = state.perfil; // tudo já vem calculado pelo servidor
    $('#planName').textContent = p.planoRotulo;
    $('#upgradeLink').textContent = p.acaoPlano;
    $('#usageText').textContent = p.pdfsUsados + ' de ' + p.pdfsLimite + ' gerações com IA neste mês'
      + (p.planoExpiraEm ? ' · Pro até ' + fmtData(p.planoExpiraEm) : '');
    $('#usageBar').style.width = p.usoPercentual + '%';
    renderPerfil();
  }

  function renderPrincipal() {
    var m = state.atual;
    var content = $('#content');
    limpar(content);
    $('#uploadBtn').hidden = !m;
    $('#subjectTitle').textContent = m ? m.nome : 'Bem-vindo ao Studiy';
    app.dataset.subject = m ? chaveCor(m.cor) : CFG.DEFAULT_SUBJECT;
    document.title = (m ? m.nome + ' — ' : '') + 'Studiy';

    if (!state.perfil.emailVerificado) {
      var reenviar = h('button', { class: 'btn btn-secondary btn-sm', type: 'button', text: 'Reenviar link' });
      reenviar.addEventListener('click', async function () {
        reenviar.disabled = true;
        try { var r = await api.post('/api/auth/reenviar-verificacao', { email: state.perfil.email }, false); toast(r.mensagem); }
        catch (e) { falha(e); reenviar.disabled = false; }
      });
      content.append(h('p', { class: 'msg info', role: 'status' },
        'Confirme seu e-mail para liberar a geração com IA e o plano Pro. ', reenviar));
    }

    if (!m) {
      content.append(h('section', { class: 'welcome' },
        h('h1', { text: 'Comece pela sua primeira matéria' }),
        h('p', { text: 'Crie uma matéria, envie o PDF da aula e o Studiy gera o resumo e os flashcards para você revisar na hora certa.' }),
        h('ol', { class: 'tips' },
          h('li', { text: 'Crie uma matéria e escolha a cor que combina com ela.' }),
          h('li', { text: 'Use "Enviar PDF" para subir o material (texto selecionável, até 10 MB).' }),
          h('li', { text: 'Gere o resumo e os flashcards e volte todo dia para revisar.' })),
        h('button', { class: 'btn btn-accent i-check', type: 'button', text: 'Criar matéria', onclick: abrirNovaMateria })));
      return;
    }

    /* Cor da matéria */
    var sw = h('fieldset', { class: 'swatches' }, h('legend', { text: 'Cor da matéria' }));
    CFG.SUBJECTS.forEach(function (s) {
      sw.append(h('button', {
        class: 'swatch', type: 'button', 'data-k': s.key, 'aria-label': s.label,
        'aria-pressed': chaveCor(m.cor) === s.key ? 'true' : 'false',
        onclick: function () { mudarCor(s.key); }
      }));
    });
    sw.append(h('button', { class: 'btn btn-secondary btn-sm grow i-trash', type: 'button', text: 'Excluir matéria', onclick: excluirMateria }));
    content.append(sw);

    /* Cartões de status */
    var g = state.perfil.gamificacao;
    var barra = h('div', { class: 'bar', title: g.xpParaProximoNivel + ' XP para o próximo nível' }, h('span'));
    barra.firstChild.style.width = g.percentualNivel + '%';
    content.append(h('div', { class: 'stats' },
      h('div', { class: 'stat' }, h('div', { class: 'label', text: 'Sequência' }), h('div', { class: 'value' }, h('span', { 'data-count': '', text: g.streakAtual }), h('small', { text: g.streakAtual === 1 ? ' dia' : ' dias' }))),
      h('div', { class: 'stat' }, h('div', { class: 'label', text: 'Nível ' + g.nivel }), h('div', { class: 'value' }, h('span', { 'data-count': '', text: g.xp }), h('small', { text: ' XP' })), barra),
      h('div', { class: 'stat' }, h('div', { class: 'label', text: 'Revisões de hoje' }), h('div', { class: 'value' }, h('span', { 'data-count': '', text: m.flashcardsPendentes }), h('small', { text: m.flashcardsPendentes === 1 ? ' cartão' : ' cartões' })))));

    /* Materiais */
    var mat = h('section', { class: 'panel' }, h('h2', { text: 'Materiais' }));
    mat.append(h('p', { class: 'hint-text', text: state.perfil.regraUso }));
    if (!state.pdfs.length) mat.append(h('p', { class: 'empty', text: 'Nenhum PDF ainda. Use "Enviar PDF" para começar.' }));
    state.pdfs.forEach(function (p) {
      var bResumo = h('button', { class: 'btn btn-accent btn-sm i-sparkle', type: 'button', text: 'Resumo' });
      var bCards = h('button', { class: 'btn btn-secondary btn-sm i-sparkle', type: 'button', text: 'Flashcards' });
      bResumo.addEventListener('click', function () { gerar(p.id, 'RESUMO', [bResumo, bCards], bResumo); });
      bCards.addEventListener('click', function () { gerar(p.id, 'FLASHCARD', [bResumo, bCards], bCards); });
      var bMd = h('button', { class: 'btn btn-secondary btn-sm i-download', type: 'button', text: '.md', 'aria-label': 'Baixar ' + p.nomeArquivo + ' em Markdown' });
      bMd.addEventListener('click', function () { baixarMarkdown(p.id, bMd); });
      mat.append(h('div', { class: 'row' }, h('span', { class: 'name', text: p.nomeArquivo }), h('span', { class: 'actions' }, bResumo, bCards, bMd)));
    });

    /* Revisão */
    var rev = h('section', { class: 'panel panel-grad' },
      h('h2', { text: 'Revisar agora' }),
      h('p', { text: m.flashcardsPendentes ? m.flashcardsPendentes + ' cartões vencem hoje. Leva poucos minutos e é o que mais ajuda a fixar o conteúdo.' : 'Nenhum cartão para revisar hoje. Gere flashcards de um PDF para começar sua rotina.' }),
      h('button', { class: 'btn btn-light i-play', type: 'button', text: 'Começar revisão', disabled: !m.flashcardsPendentes, onclick: abrirRevisao }));

    /* Resumos */
    var res = h('section', { class: 'panel' }, h('h2', { text: 'Resumos' }));
    if (!state.resumos.length) res.append(h('p', { class: 'empty', text: 'Os resumos gerados aparecem aqui. Escolha um PDF em "Materiais" e toque em "Resumo".' }));
    state.resumos.forEach(function (r) {
      var titulo = (r.conteudo || '').split('\n').find(function (l) { return l.trim(); }) || 'Resumo';
      res.append(h('details', { class: 'resumo' },
        h('summary', { text: fmtData(r.criadoEm) + ' · ' + titulo.slice(0, 60) }),
        h('div', { class: 'prewrap', text: r.conteudo })));
    });

    content.append(h('div', { class: 'panels' }, ordemDosPaineis(m, mat, rev)), h('div', { class: 'panels' }, res));
    if (window.Motion) Motion.observar(content); // números sobem (data-count)
  }

  /* Ordem dos dois painéis principais (no celular, o primeiro fica no topo da tela).
     m = matéria atual (m.flashcardsPendentes vem do servidor); state.pdfs = PDFs desta matéria.
     Devolve um DocumentFragment com os painéis na ordem escolhida. */
  function ordemDosPaineis(m, mat, rev) {
    var frag = document.createDocumentFragment();
    // TODO(human): decidir qual painel vem primeiro e anexar os dois em frag
    frag.append(mat, rev);
    return frag;
  }

  /* ---------- Ações ---------- */
  async function mudarCor(chave) {
    var m = state.atual;
    try {
      await api.put('/api/materias/' + m.id, { nome: m.nome, cor: chave });
      m.cor = chave;
      app.dataset.subject = chave;
      renderPrincipal();
    } catch (e) { falha(e); }
  }

  async function excluirMateria() {
    var m = state.atual;
    if (!window.confirm('Excluir "' + m.nome + '" e todos os PDFs, resumos e flashcards dela? Esta ação não pode ser desfeita.')) return;
    try {
      await api.del('/api/materias/' + m.id);
      await recarregarMaterias();
      toast('Matéria excluída.');
      await selecionar(state.materias.length ? state.materias[0].id : null);
    } catch (e) { falha(e); }
  }

  /* PDF já convertido em Markdown no servidor (sem IA): baixa como .md */
  async function baixarMarkdown(pdfId, btn) {
    btn.disabled = true;
    try {
      var md = await api.get('/api/materias/' + state.atual.id + '/pdfs/' + pdfId + '/markdown');
      var blob = new Blob([md.conteudo], { type: 'text/markdown;charset=utf-8' });
      var a = h('a', { href: URL.createObjectURL(blob), download: md.nomeArquivo });
      document.body.append(a); a.click(); a.remove();
      setTimeout(function () { URL.revokeObjectURL(a.href); }, 1000);
    } catch (e) { falha(e); }
    finally { btn.disabled = false; }
  }

  async function gerar(pdfId, tipo, botoes, ativo) {
    var rotulo = ativo.textContent;
    botoes.forEach(function (b) { b.disabled = true; });
    ativo.textContent = 'Gerando...';
    ativo.setAttribute('aria-busy', 'true');
    try {
      var r = await api.post('/api/materias/' + state.atual.id + '/gerar', { pdfId: pdfId, tipo: tipo });
      await Promise.all([recarregarPerfil(), recarregarMaterias()]);
      var id = state.atual ? state.atual.id : null;
      // reaproveitado: o mesmo conteúdo já tinha sido gerado; o servidor não gastou o limite do mês
      if (r && r.reaproveitado) toast('Este material já tinha sido gerado. Reaproveitamos, sem gastar seu limite.');
      else toast(tipo === 'RESUMO' ? 'Resumo gerado!' : 'Flashcards gerados!');
      await selecionar(id);
    } catch (e) {
      falha(e);
      botoes.forEach(function (b) { b.disabled = false; });
      ativo.textContent = rotulo;
      ativo.removeAttribute('aria-busy');
    }
  }

  $('#uploadBtn').addEventListener('click', function () { $('#fileInput').click(); });
  $('#fileInput').addEventListener('change', async function (ev) {
    var arquivo = ev.target.files[0];
    ev.target.value = '';
    if (!arquivo || !state.atual) return;
    var form = new FormData();
    form.append('arquivo', arquivo);
    var btn = $('#uploadBtn');
    btn.disabled = true; btn.textContent = 'Enviando...'; btn.setAttribute('aria-busy', 'true');
    try {
      await api.upload('/api/materias/' + state.atual.id + '/pdfs', form);
      toast('PDF enviado. Agora gere o resumo ou os flashcards.');
      await selecionar(state.atual.id);
    } catch (e) { falha(e); }
    finally { btn.disabled = false; btn.textContent = 'Enviar PDF'; btn.removeAttribute('aria-busy'); }
  });

  /* ---------- Nova matéria ---------- */
  function abrirNovaMateria() {
    var dlg = $('#newDialog');
    $('#newName').value = '';
    $('#newMsg').hidden = true;
    var box = $('#newSwatches');
    limpar(box);
    var escolhida = CFG.DEFAULT_SUBJECT;
    function marcar() {
      box.querySelectorAll('.swatch').forEach(function (b) { b.setAttribute('aria-pressed', b.dataset.k === escolhida ? 'true' : 'false'); });
    }
    CFG.SUBJECTS.forEach(function (s) {
      box.append(h('button', { class: 'swatch', type: 'button', 'data-k': s.key, 'aria-label': s.label, onclick: function () { escolhida = s.key; marcar(); } }));
    });
    marcar();
    dlg.dataset.cor = escolhida;
    $('#newForm').onsubmit = async function (ev) {
      ev.preventDefault();
      try {
        var criada = await api.post('/api/materias', { nome: $('#newName').value.trim(), cor: escolhida });
        dlg.close();
        await recarregarMaterias();
        await selecionar(criada.id);
      } catch (e) { var m = $('#newMsg'); m.textContent = e.message; m.hidden = false; }
    };
    dlg.showModal();
    $('#newName').focus();
  }

  /* ---------- Revisão (SM-2) ---------- */
  async function abrirRevisao() {
    var dlg = $('#reviewDialog');
    var box = $('#reviewBody');
    var fila, opcoes;
    try {
      var rev = await api.get('/api/materias/' + state.atual.id + '/flashcards/revisao');
      fila = rev.cartoes; opcoes = rev.opcoes; // botões e escala SM-2 decididos pelo servidor
    } catch (e) { falha(e); return; }
    if (!fila.length) { toast('Nenhum cartão para revisar hoje.'); return; }
    var i = 0;
    var revisados = 0; // quantos foram enviados desde a última atualização do painel
    async function atualizarPainel() {
      if (!revisados) return;
      revisados = 0;
      // espera perfil (XP) E matérias (pendentes) antes de redesenhar, senão pinta dado velho
      try { await Promise.all([recarregarPerfil(), recarregarMaterias()]); renderPrincipal(); } catch (e) { falha(e); }
    }
    // fechar no meio (botão ou Esc) também atualiza XP e "Revisões de hoje"
    dlg.addEventListener('close', atualizarPainel, { once: true });
    function mostrar() {
      limpar(box);
      if (i >= fila.length) {
        box.append(h('p', { class: 'card-q', text: 'Revisão concluída! 🎉' }));
        atualizarPainel();
        return;
      }
      var c = fila[i];
      // Flashcard com flip 3D (css/motion.css): frente = pergunta, verso = resposta
      var cartao = h('div', { class: 'flip flashcard' }, h('div', { class: 'flip-inner' },
        h('div', { class: 'flip-face flip-front' }, h('p', { class: 'card-label', text: 'Pergunta' }), h('p', { class: 'card-q', text: c.pergunta })),
        h('div', { class: 'flip-face flip-back', 'aria-hidden': 'true' }, h('p', { class: 'card-label', text: 'Resposta' }), h('div', { class: 'card-a', text: c.resposta }))));
      var notas = h('div', { class: 'rate', hidden: true });
      opcoes.forEach(function (o) {
        notas.append(h('button', { class: 'btn btn-accent btn-sm', type: 'button', text: o.rotulo, onclick: async function () {
          try { await api.post('/api/materias/' + state.atual.id + '/flashcards/' + c.id + '/revisar', { resposta: o.valor }); i++; revisados++; mostrar(); }
          catch (e) { falha(e); }
        } }));
      });
      var ver = h('button', { class: 'btn btn-primary', type: 'button', text: 'Ver resposta' });
      ver.addEventListener('click', function () {
        cartao.classList.add('is-flipped');
        cartao.querySelector('.flip-back').removeAttribute('aria-hidden');
        cartao.querySelector('.flip-front').setAttribute('aria-hidden', 'true');
        notas.hidden = false; ver.hidden = true;
        notas.querySelector('button').focus();
      });
      box.append(h('p', { class: 'empty', text: 'Cartão ' + (i + 1) + ' de ' + fila.length }), cartao, ver, notas);
    }
    mostrar();
    dlg.showModal();
  }

  /* ---------- Perfil e conta ---------- */
  function iniciais(nome) {
    var p = (nome || '?').trim().split(/\s+/);
    return ((p[0] || '?')[0] + (p.length > 1 ? p[p.length - 1][0] : '')).toUpperCase();
  }
  function pintarAvatar(el, perfil) {
    limpar(el);
    if (perfil.avatar && perfil.avatar.indexOf('data:image/') === 0) {
      el.append(h('img', { src: perfil.avatar, alt: '' }));
    } else {
      el.textContent = iniciais(perfil.nome);
    }
  }
  function renderPerfil() {
    var p = state.perfil;
    if (!p) return;
    $('#sideName').textContent = p.nome;
    pintarAvatar($('#sideAvatar'), p);
    $('#dlgName').textContent = p.nome;
    $('#dlgEmail').textContent = p.email;
    pintarAvatar($('#dlgAvatar'), p);
    $('#avatarDel').hidden = !p.avatar;
    if (p.admin && !$('#adminLink')) { // o servidor diz quem é admin; o painel em si exige MFA
      $('#sideName').after(h('a', { id: 'adminLink', class: 'btn btn-secondary btn-sm', href: 'admin.html', text: 'Admin' }));
    }
  }
  function accMsg(texto, ok) {
    var m = $('#accMsg');
    m.textContent = texto;
    m.className = 'msg ' + (ok ? 'info' : 'error');
    m.hidden = false;
  }
  async function acao(fn, okTexto, botao) {
    $('#accMsg').hidden = true;
    var pronto = botao && window.Botao ? Botao.carregando(botao) : function () {}; // spinner enquanto espera
    try { await fn(); if (okTexto) accMsg(okTexto, true); }
    catch (e) { accMsg(e.message || 'Algo deu errado.', false); }
    finally { pronto(); }
  }

  $('#accountBtn').addEventListener('click', function () {
    if (!state.perfil) return; // ainda carregando
    $('#accMsg').hidden = true;
    $('#delForm').hidden = true;
    $('#accNome').value = state.perfil.nome;
    $('#accApelido').value = state.perfil.apelido || '';
    ['accNovoEmail', 'accSenhaEmail', 'accSenhaAtual', 'accSenhaNova', 'delPass'].forEach(function (id) { $('#' + id).value = ''; });
    renderPerfil();
    $('#accountDialog').showModal();
  });

  $('#nameForm').addEventListener('submit', function (ev) {
    ev.preventDefault();
    acao(async function () {
      state.perfil = await api.put('/api/usuarios/me', { nome: $('#accNome').value.trim() });
      renderPerfil();
    }, 'Nome atualizado.', ev.submitter);
  });

  $('#apelidoForm').addEventListener('submit', function (ev) {
    ev.preventDefault();
    var saindo = !$('#accApelido').value.trim();
    acao(async function () {
      state.perfil = await api.put('/api/usuarios/me/apelido', { apelido: $('#accApelido').value.trim() });
      $('#accApelido').value = state.perfil.apelido || '';
      esquecerPreviasDoRanking();          // a participação mudou: a prévia salva ficou velha
      if (state.tela === 'ranking') carregarRanking(state.rankingTipo);
      renderPerfil();
    }, saindo ? 'Você saiu do ranking.' : 'Apelido salvo. Você aparece no ranking na próxima atualização (até 5 minutos).', ev.submitter);
  });

  $('#emailForm').addEventListener('submit', function (ev) {
    ev.preventDefault();
    acao(async function () {
      await api.put('/api/usuarios/me/email', { novoEmail: $('#accNovoEmail').value, senha: $('#accSenhaEmail').value });
      state.perfil = await api.get('/api/usuarios/me');
      $('#accNovoEmail').value = ''; $('#accSenhaEmail').value = '';
      renderPerfil();
    }, 'E-mail atualizado.', ev.submitter);
  });

  $('#passForm').addEventListener('submit', function (ev) {
    ev.preventDefault();
    acao(async function () {
      await api.put('/api/usuarios/me/senha', { senhaAtual: $('#accSenhaAtual').value, novaSenha: $('#accSenhaNova').value });
      $('#accSenhaAtual').value = ''; $('#accSenhaNova').value = '';
    }, 'Senha alterada. As outras sessões foram encerradas.', ev.submitter);
  });

  /* Foto: recorta em quadrado e reduz para 256x256 no navegador (fica leve) antes de enviar */
  function prepararFoto(arquivo) {
    return new Promise(function (resolve, reject) {
      // Só recorta/reduz para 256x256 antes de enviar; tipo, tamanho e assinatura são validados no servidor.
      var leitor = new FileReader();
      leitor.onerror = function () { reject(new Error('Não foi possível ler a imagem.')); };
      leitor.onload = function () {
        var img = new Image();
        img.onerror = function () { reject(new Error('Imagem inválida.')); };
        img.onload = function () {
          var lado = Math.min(img.width, img.height), T = 256;
          var cv = document.createElement('canvas');
          cv.width = T; cv.height = T;
          cv.getContext('2d').drawImage(img, (img.width - lado) / 2, (img.height - lado) / 2, lado, lado, 0, 0, T, T);
          resolve(cv.toDataURL('image/jpeg', 0.85));
        };
        img.src = leitor.result;
      };
      leitor.readAsDataURL(arquivo);
    });
  }
  $('#avatarBtn').addEventListener('click', function () { $('#avatarFile').click(); });
  $('#avatarFile').addEventListener('change', function (ev) {
    var arquivo = ev.target.files[0];
    ev.target.value = '';
    if (!arquivo) return;
    acao(async function () {
      var dataUrl = await prepararFoto(arquivo);
      state.perfil = await api.put('/api/usuarios/me/avatar', { avatar: dataUrl });
      renderPerfil();
    }, 'Foto atualizada.', $('#avatarBtn'));
  });
  $('#avatarDel').addEventListener('click', function () {
    acao(async function () {
      state.perfil = await api.put('/api/usuarios/me/avatar', { avatar: null });
      renderPerfil();
    }, 'Foto removida.', $('#avatarDel'));
  });

  /* ---------- Sair, exportar e excluir (LGPD) ---------- */
  $('#logoutBtn').addEventListener('click', async function () {
    try { await api.post('/api/auth/logout'); } catch (e) { /* sai mesmo assim */ }
    api.esquecerSessao();
    window.location.href = 'login.html';
  });
  $('#exportBtn').addEventListener('click', async function () {
    var pronto = Botao.carregando($('#exportBtn'), 'Preparando…');
    try {
      var dados = await api.get('/api/usuarios/me/exportar');
      var blob = new Blob([JSON.stringify(dados, null, 2)], { type: 'application/json' });
      var a = h('a', { href: URL.createObjectURL(blob), download: 'meus-dados-studiy.json' });
      document.body.append(a); a.click(); a.remove();
    } catch (e) { accMsg(e.message, false); }
    finally { pronto(); }
  });
  $('#delStart').addEventListener('click', function () { $('#delForm').hidden = false; $('#delPass').focus(); });
  $('#delForm').addEventListener('submit', function (ev) {
    ev.preventDefault();
    acao(async function () {
      await api.del('/api/usuarios/me', { senha: $('#delPass').value });
      api.esquecerSessao();
      window.location.href = 'index.html';
    }, null, ev.submitter);
  });
  document.querySelectorAll('[data-close]').forEach(function (b) {
    b.addEventListener('click', function () { b.closest('dialog').close(); });
  });

  $('#rankingBtn').addEventListener('click', abrirRanking);

  init();
})();
