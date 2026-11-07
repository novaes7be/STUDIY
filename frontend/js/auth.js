/* Telas de login, cadastro e recuperação de senha. */
(function () {
  'use strict';
  var page = document.body.dataset.page;
  var form = document.getElementById('authForm');
  var msg = document.getElementById('msg');

  function show(text, kind) {
    msg.textContent = text;
    msg.className = 'msg ' + (kind || 'error');
    msg.hidden = false;
  }
  function busy(on) {
    document.querySelectorAll('button[type="submit"]').forEach(function (b) {
      b.disabled = on;
      if (on) b.setAttribute('aria-busy', 'true'); else b.removeAttribute('aria-busy'); // spinner (css/buttons.css)
    });
  }
  function entrar() {
    api.marcarLogado(); // a sessão em si fica nos cookies HttpOnly que o servidor gravou
    window.location.href = 'dashboard.html';
  }
  async function enviar(fn) {
    msg.hidden = true;
    busy(true);
    try { await fn(); }
    catch (e) { show(e.message || 'Algo deu errado.'); }
    finally { busy(false); }
  }
  var v = function (id) { return document.getElementById(id).value; };

  // Aviso claro se o backend estiver desligado (em vez de só falhar ao enviar o formulário).
  api.ping().then(function (ok) {
    if (!ok) {
      show('Não consegui falar com o servidor em ' + api.apiBase + '. Inicie o backend (porta 8080) e recarregue a página. '
        + 'Teste abrindo ' + api.apiBase + '/api/saude no navegador: deve aparecer {"status":"ok"}.', 'error');
    }
  });

  if (api.pareceLogado() && (page === 'login' || page === 'cadastro')) {
    window.location.replace('dashboard.html');
  }

  if (page === 'login') {
    form.addEventListener('submit', function (ev) {
      ev.preventDefault();
      enviar(async function () {
        await api.post('/api/auth/login', { email: v('email'), senha: v('senha') }, false);
        entrar();
      });
    });
  }

  if (page === 'cadastro') {
    form.addEventListener('submit', function (ev) {
      ev.preventDefault();
      enviar(async function () {
        // O aceite vai como foi marcado; quem recusa o cadastro sem aceite é o servidor.
        var resp = await api.post('/api/auth/registrar', {
          nome: v('nome'), email: v('email'), senha: v('senha'), aceitoTermos: document.getElementById('aceito').checked
        }, false);
        // Resposta sempre genérica (o servidor não revela se o e-mail já tem conta): só exibe a mensagem.
        form.reset();
        show(resp.mensagem, 'info');
      });
    });
  }

  if (page === 'recuperar') {
    // O token vem no fragmento (#token=...), que o navegador não envia ao servidor nem a terceiros.
    var token = new URLSearchParams(window.location.hash.slice(1)).get('token');
    var etapaEmail = document.getElementById('etapaEmail');
    var etapaSenha = document.getElementById('etapaSenha');
    if (token) { etapaEmail.hidden = true; etapaSenha.hidden = false; }

    etapaEmail.addEventListener('submit', function (ev) {
      ev.preventDefault();
      enviar(async function () {
        var r = await api.post('/api/auth/esqueci-senha', { email: v('email') }, false);
        show(r.mensagem, 'info');
      });
    });
    etapaSenha.addEventListener('submit', function (ev) {
      ev.preventDefault();
      enviar(async function () {
        await api.post('/api/auth/redefinir-senha', { token: token, novaSenha: v('novaSenha') }, false);
        history.replaceState(null, '', 'recuperar-senha.html');
        show('Senha alterada. Redirecionando para o login...', 'info');
        setTimeout(function () { window.location.href = 'login.html'; }, 1500);
      });
    });
  }

  if (page === 'verificar') {
    // O token vem no fragmento (#token=...), que o navegador não envia ao servidor nem a terceiros.
    var tk = new URLSearchParams(window.location.hash.slice(1)).get('token');
    var formReenvio = document.getElementById('etapaEmail');
    if (tk) {
      formReenvio.hidden = true;
      show('Confirmando seu e-mail…', 'info');
      api.post('/api/auth/verificar-email', { token: tk }, false).then(function () {
        history.replaceState(null, '', 'verificar-email.html');
        show('E-mail confirmado! Redirecionando para o login…', 'info');
        setTimeout(function () { window.location.href = 'login.html'; }, 1500);
      }).catch(function (e) {
        formReenvio.hidden = false;
        show((e.message || 'Link inválido ou expirado.') + ' Peça um novo link abaixo.');
      });
    }
    formReenvio.addEventListener('submit', function (ev) {
      ev.preventDefault();
      enviar(async function () {
        var r = await api.post('/api/auth/reenviar-verificacao', { email: v('email') }, false);
        show(r.mensagem, 'info');
      });
    });
  }
})();
