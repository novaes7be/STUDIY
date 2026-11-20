/* Textos do JavaScript no idioma da página.
     t("chave", "Texto em português com {n} itens", { n: 3 })   (no código, com aspas simples)
   Em português, devolve o texto passado no código. Nas páginas /en/ e /es/, o build
   (deploy/i18n-front.mjs) carrega antes js/textos-<idioma>.js, que define window.STUDIY_TEXTOS. */
(function () {
  var dic = window.STUDIY_TEXTOS || {};
  var idioma = document.documentElement.lang || 'pt-BR';

  window.t = function (chave, ptBR, vars) {
    var s = typeof dic[chave] === 'string' ? dic[chave] : ptBR;
    if (!vars) return s;
    return s.replace(/\{(\w+)\}/g, function (m, k) { return Object.prototype.hasOwnProperty.call(vars, k) ? String(vars[k]) : m; });
  };
  /* Idioma da página para datas e números (Intl / toLocaleString). */
  window.IDIOMA = idioma;
})();
