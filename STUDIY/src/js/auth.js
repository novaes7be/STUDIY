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
    document.querySelectorAll('button[type="submit"]').forEach(function (b) { b.disabled = on; });
  }
  function entrar(resp) {
    api.saveToken(resp.token);
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

  if (api.hasToken() && (page === 'login' || page === 'cadastro')) {
    window.location.replace('dashboard.html');
  }

  if (page === 'login') {
    form.addEventListener('submit', function (ev) {
      ev.preventDefault();
      enviar(async function () {
        entrar(await api.post('/api/auth/login', { email: v('email'), senha: v('senha') }, false));
      });
    });
  }

  if (page === 'cadastro') {
    form.addEventListener('submit', function (ev) {
      ev.preventDefault();
      enviar(async function () {
        if (!document.getElementById('aceito').checked) {
          throw new Error('É preciso aceitar os Termos e a Política de Privacidade.');
        }
        entrar(await api.post('/api/auth/registrar', {
          nome: v('nome'), email: v('email'), senha: v('senha'), aceitoTermos: true
        }, false));
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
})();
