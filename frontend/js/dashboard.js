/* Painel do Studiy. Todo conteúdo vindo da API (nomes, resumos, flashcards gerados por IA)
   é inserido com textContent/createTextNode, NUNCA como HTML (proteção contra XSS). */
(function () {
  'use strict';
  var CFG = window.STUDIY;
  var state = { perfil: null, materias: [], atual: null, pdfs: [], resumos: [] };

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
        class: 'subj', type: 'button', text: m.nome,
        'aria-current': state.atual && state.atual.id === m.id ? 'true' : 'false',
        onclick: function () { selecionar(m.id); }
      }));
    });
    box.append(h('button', { class: 'subj add', type: 'button', text: '+ Nova matéria', onclick: abrirNovaMateria }));
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
      var reenviar = h('button', { class: 'btn btn-ghost btn-sm', type: 'button', text: 'Reenviar link' });
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
        h('button', { class: 'btn btn-grad i-check', type: 'button', text: 'Criar matéria', onclick: abrirNovaMateria })));
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
    sw.append(h('button', { class: 'btn btn-ghost btn-sm grow i-trash', type: 'button', text: 'Excluir matéria', onclick: excluirMateria }));
    content.append(sw);

    /* Cartões de status */
    var g = state.perfil.gamificacao;
    var barra = h('div', { class: 'bar', title: g.xpParaProximoNivel + ' XP para o próximo nível' }, h('span'));
    barra.firstChild.style.width = g.percentualNivel + '%';
    content.append(h('div', { class: 'stats' },
      h('div', { class: 'stat stat-dark' }, h('div', { class: 'label', text: 'Sequência' }), h('div', { class: 'value', text: g.streakAtual + (g.streakAtual === 1 ? ' dia' : ' dias') })),
      h('div', { class: 'stat stat-grad' }, h('div', { class: 'label', text: 'Nível ' + g.nivel }), h('div', { class: 'value', text: g.xp + ' XP' }), barra),
      h('div', { class: 'stat stat-deep' }, h('div', { class: 'label', text: 'Revisões de hoje' }), h('div', { class: 'value', text: m.flashcardsPendentes + (m.flashcardsPendentes === 1 ? ' cartão' : ' cartões') }))));

    /* Materiais */
    var mat = h('section', { class: 'panel' }, h('h2', { text: 'Materiais' }));
    mat.append(h('p', { class: 'hint-text', text: state.perfil.regraUso }));
    if (!state.pdfs.length) mat.append(h('p', { class: 'empty', text: 'Nenhum PDF ainda. Use "Enviar PDF" para começar.' }));
    state.pdfs.forEach(function (p) {
      var bResumo = h('button', { class: 'btn btn-grad btn-sm i-sparkle', type: 'button', text: 'Resumo' });
      var bCards = h('button', { class: 'btn btn-ghost btn-sm i-sparkle', type: 'button', text: 'Flashcards' });
      bResumo.addEventListener('click', function () { gerar(p.id, 'RESUMO', [bResumo, bCards], bResumo); });
      bCards.addEventListener('click', function () { gerar(p.id, 'FLASHCARD', [bResumo, bCards], bCards); });
      mat.append(h('div', { class: 'row' }, h('span', { class: 'name', text: p.nomeArquivo }), h('span', { class: 'actions' }, bResumo, bCards)));
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

  async function gerar(pdfId, tipo, botoes, ativo) {
    var rotulo = ativo.textContent;
    botoes.forEach(function (b) { b.disabled = true; });
    ativo.textContent = 'Gerando...';
    try {
      await api.post('/api/materias/' + state.atual.id + '/gerar', { pdfId: pdfId, tipo: tipo });
      await Promise.all([recarregarPerfil(), recarregarMaterias()]);
      var id = state.atual ? state.atual.id : null;
      toast(tipo === 'RESUMO' ? 'Resumo gerado!' : 'Flashcards gerados!');
      await selecionar(id);
    } catch (e) {
      falha(e);
      botoes.forEach(function (b) { b.disabled = false; });
      ativo.textContent = rotulo;
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
    btn.disabled = true; btn.textContent = 'Enviando...';
    try {
      await api.upload('/api/materias/' + state.atual.id + '/pdfs', form);
      toast('PDF enviado. Agora gere o resumo ou os flashcards.');
      await selecionar(state.atual.id);
    } catch (e) { falha(e); }
    finally { btn.disabled = false; btn.textContent = 'Enviar PDF'; }
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
    function mostrar() {
      limpar(box);
      if (i >= fila.length) {
        box.append(h('p', { class: 'card-q', text: 'Revisão concluída! 🎉' }));
        recarregarPerfil();
        recarregarMaterias().then(function () { renderPrincipal(); });
        return;
      }
      var c = fila[i];
      var resposta = h('div', { class: 'card-a', text: c.resposta, hidden: true });
      var notas = h('div', { class: 'rate', hidden: true });
      opcoes.forEach(function (o) {
        notas.append(h('button', { class: 'btn btn-grad btn-sm', type: 'button', text: o.rotulo, onclick: async function () {
          try { await api.post('/api/materias/' + state.atual.id + '/flashcards/' + c.id + '/revisar', { resposta: o.valor }); i++; mostrar(); }
          catch (e) { falha(e); }
        } }));
      });
      var ver = h('button', { class: 'btn btn-dark', type: 'button', text: 'Ver resposta' });
      ver.addEventListener('click', function () { resposta.hidden = false; notas.hidden = false; ver.hidden = true; });
      box.append(h('p', { class: 'empty', text: 'Cartão ' + (i + 1) + ' de ' + fila.length }),
        h('p', { class: 'card-q', text: c.pergunta }), resposta, ver, notas);
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
      $('#sideName').after(h('a', { id: 'adminLink', class: 'btn btn-ghost btn-sm', href: 'admin.html', text: 'Admin' }));
    }
  }
  function accMsg(texto, ok) {
    var m = $('#accMsg');
    m.textContent = texto;
    m.className = 'msg ' + (ok ? 'info' : 'error');
    m.hidden = false;
  }
  async function acao(fn, okTexto) {
    $('#accMsg').hidden = true;
    try { await fn(); if (okTexto) accMsg(okTexto, true); }
    catch (e) { accMsg(e.message || 'Algo deu errado.', false); }
  }

  $('#accountBtn').addEventListener('click', function () {
    if (!state.perfil) return; // ainda carregando
    $('#accMsg').hidden = true;
    $('#delForm').hidden = true;
    $('#accNome').value = state.perfil.nome;
    ['accNovoEmail', 'accSenhaEmail', 'accSenhaAtual', 'accSenhaNova', 'delPass'].forEach(function (id) { $('#' + id).value = ''; });
    renderPerfil();
    $('#accountDialog').showModal();
  });

  $('#nameForm').addEventListener('submit', function (ev) {
    ev.preventDefault();
    acao(async function () {
      state.perfil = await api.put('/api/usuarios/me', { nome: $('#accNome').value.trim() });
      renderPerfil();
    }, 'Nome atualizado.');
  });

  $('#emailForm').addEventListener('submit', function (ev) {
    ev.preventDefault();
    acao(async function () {
      await api.put('/api/usuarios/me/email', { novoEmail: $('#accNovoEmail').value, senha: $('#accSenhaEmail').value });
      state.perfil = await api.get('/api/usuarios/me');
      $('#accNovoEmail').value = ''; $('#accSenhaEmail').value = '';
      renderPerfil();
    }, 'E-mail atualizado.');
  });

  $('#passForm').addEventListener('submit', function (ev) {
    ev.preventDefault();
    acao(async function () {
      await api.put('/api/usuarios/me/senha', { senhaAtual: $('#accSenhaAtual').value, novaSenha: $('#accSenhaNova').value });
      $('#accSenhaAtual').value = ''; $('#accSenhaNova').value = '';
    }, 'Senha alterada. As outras sessões foram encerradas.');
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
    }, 'Foto atualizada.');
  });
  $('#avatarDel').addEventListener('click', function () {
    acao(async function () {
      state.perfil = await api.put('/api/usuarios/me/avatar', { avatar: null });
      renderPerfil();
    }, 'Foto removida.');
  });

  /* ---------- Sair, exportar e excluir (LGPD) ---------- */
  $('#logoutBtn').addEventListener('click', async function () {
    try { await api.post('/api/auth/logout'); } catch (e) { /* sai mesmo assim */ }
    api.esquecerSessao();
    window.location.href = 'login.html';
  });
  $('#exportBtn').addEventListener('click', async function () {
    try {
      var dados = await api.get('/api/usuarios/me/exportar');
      var blob = new Blob([JSON.stringify(dados, null, 2)], { type: 'application/json' });
      var a = h('a', { href: URL.createObjectURL(blob), download: 'meus-dados-studiy.json' });
      document.body.append(a); a.click(); a.remove();
    } catch (e) { accMsg(e.message, false); }
  });
  $('#delStart').addEventListener('click', function () { $('#delForm').hidden = false; $('#delPass').focus(); });
  $('#delForm').addEventListener('submit', function (ev) {
    ev.preventDefault();
    acao(async function () {
      await api.del('/api/usuarios/me', { senha: $('#delPass').value });
      api.esquecerSessao();
      window.location.href = 'index.html';
    });
  });
  document.querySelectorAll('[data-close]').forEach(function (b) {
    b.addEventListener('click', function () { b.closest('dialog').close(); });
  });

  init();
})();
