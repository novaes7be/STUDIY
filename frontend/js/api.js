/* Cliente da API. A sessão vive em cookies HttpOnly definidos pelo servidor: este código NUNCA vê token.
   Escritas levam o header X-XSRF-TOKEN (double-submit CSRF, valor lido do cookie XSRF-TOKEN).
   Com 401, tenta renovar a sessão uma vez (refresh token rotativo) e repete a chamada.
   O front NUNCA decide permissões: toda regra é validada no backend. */
(function () {
  'use strict';
  var BASE = window.STUDIY.API_BASE;
  // Só um indicativo para a interface (ex.: trocar "Entrar" por "Meu painel"); não autoriza nada.
  var HINT_KEY = 'studiy-logado';

  function ApiError(status, message, fields) {
    this.status = status; this.message = message; this.fields = fields || null;
  }

  var hint = {
    get: function () { try { return sessionStorage.getItem(HINT_KEY) === '1'; } catch (e) { return false; } },
    set: function () { try { sessionStorage.setItem(HINT_KEY, '1'); } catch (e) { /* ignora */ } },
    clear: function () { try { sessionStorage.removeItem(HINT_KEY); } catch (e) { /* ignora */ } }
  };

  function lerCookie(nome) {
    var partes = document.cookie ? document.cookie.split('; ') : [];
    for (var i = 0; i < partes.length; i++) {
      var p = partes[i].split('=');
      if (p[0] === nome) return decodeURIComponent(p.slice(1).join('='));
    }
    return null;
  }

  async function garantirCsrf() {
    if (lerCookie('XSRF-TOKEN')) return;
    try { await fetch(BASE + '/api/auth/csrf', { credentials: 'include' }); } catch (e) { /* tratado na chamada */ }
  }

  async function bruto(path, opts) {
    var metodo = opts.method || 'GET';
    var headers = {};
    var body;
    if (opts.form) { body = opts.form; }
    else if (opts.body !== undefined) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(opts.body); }
    if (metodo !== 'GET') {
      await garantirCsrf();
      var xsrf = lerCookie('XSRF-TOKEN');
      if (xsrf) headers['X-XSRF-TOKEN'] = xsrf;
    }
    try {
      return await fetch(BASE + path, { method: metodo, headers: headers, body: body, credentials: 'include' });
    } catch (e) {
      throw new ApiError(0, 'Não foi possível conectar ao servidor (' + (BASE || window.location.origin) + '). Confirme que o backend está rodando e tente de novo.');
    }
  }

  var renovando = null;
  function renovar() {
    if (!renovando) {
      renovando = bruto('/api/auth/refresh', { method: 'POST' })
        .then(function (r) { return r.ok; }, function () { return false; })
        .finally(function () { renovando = null; });
    }
    return renovando;
  }

  async function request(path, opts) {
    opts = opts || {};
    var auth = opts.auth !== false;
    var res = await bruto(path, opts);
    if (res.status === 401 && auth) {
      if (await renovar()) res = await bruto(path, opts);
      if (res.status === 401) {
        hint.clear();
        window.location.replace('login.html');
        throw new ApiError(401, 'Sessão expirada. Entre novamente.');
      }
    }
    var data = null;
    if ((res.headers.get('content-type') || '').indexOf('application/json') !== -1) {
      try { data = await res.json(); } catch (e) { data = null; }
    }
    if (!res.ok) {
      var msg = (data && data.erro) || 'Algo deu errado. Tente novamente.';
      if (data && data.campos) msg = Object.values(data.campos).join(' ');
      throw new ApiError(res.status, msg, data && data.campos);
    }
    return data;
  }

  window.api = {
    /** true se a API responde (endpoint público /api/saude). */
    ping: async function () {
      try {
        var ctrl = new AbortController();
        var t = setTimeout(function () { ctrl.abort(); }, 4000);
        var r = await fetch(BASE + '/api/saude', { signal: ctrl.signal });
        clearTimeout(t);
        return r.ok;
      } catch (e) { return false; }
    },
    apiBase: BASE || window.location.origin,
    /** Indicativo de interface apenas; quem decide se há sessão é o servidor (401). */
    pareceLogado: hint.get,
    marcarLogado: hint.set,
    esquecerSessao: hint.clear,
    get: function (p) { return request(p); },
    post: function (p, body, auth) { return request(p, { method: 'POST', body: body === undefined ? {} : body, auth: auth }); },
    put: function (p, body) { return request(p, { method: 'PUT', body: body }); },
    del: function (p, body) { return request(p, { method: 'DELETE', body: body }); },
    upload: function (p, form) { return request(p, { method: 'POST', form: form }); },
    ApiError: ApiError
  };
})();
