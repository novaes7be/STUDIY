/* Cliente da API. O token fica em sessionStorage (some ao fechar a aba).
   O front NUNCA decide permissões: toda regra é validada no backend. */
(function () {
  'use strict';
  var TOKEN_KEY = 'studiy-token';
  var BASE = window.STUDIY.API_BASE;

  function ApiError(status, message, fields) {
    this.status = status; this.message = message; this.fields = fields || null;
  }

  var token = {
    get: function () { try { return sessionStorage.getItem(TOKEN_KEY); } catch (e) { return null; } },
    set: function (v) { try { sessionStorage.setItem(TOKEN_KEY, v); } catch (e) { /* ignora */ } },
    clear: function () { try { sessionStorage.removeItem(TOKEN_KEY); } catch (e) { /* ignora */ } }
  };

  async function request(path, opts) {
    opts = opts || {};
    var headers = {};
    var auth = opts.auth !== false;
    if (auth && token.get()) headers['Authorization'] = 'Bearer ' + token.get();
    var body;
    if (opts.form) { body = opts.form; }
    else if (opts.body !== undefined) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(opts.body); }

    var res;
    try {
      res = await fetch(BASE + path, { method: opts.method || 'GET', headers: headers, body: body });
    } catch (e) {
      throw new ApiError(0, 'Não foi possível conectar ao servidor (' + BASE + '). Confirme que o backend está rodando e tente de novo.');
    }
    if (res.status === 401 && auth) {
      token.clear();
      window.location.replace('login.html');
      throw new ApiError(401, 'Sessão expirada. Entre novamente.');
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
    apiBase: BASE,
    hasToken: function () { return !!token.get(); },
    saveToken: token.set,
    clearToken: token.clear,
    get: function (p) { return request(p); },
    post: function (p, body, auth) { return request(p, { method: 'POST', body: body === undefined ? {} : body, auth: auth }); },
    put: function (p, body) { return request(p, { method: 'PUT', body: body }); },
    del: function (p, body) { return request(p, { method: 'DELETE', body: body }); },
    upload: function (p, form) { return request(p, { method: 'POST', form: form }); },
    ApiError: ApiError
  };
})();
