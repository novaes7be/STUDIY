/* Estado "carregando" dos botões: spinner (css/buttons.css) + texto + desabilitado.
   Uso: var pronto = Botao.carregando(btn, 'Enviando…'); try { await ... } finally { pronto(); } */
(function () {
  'use strict';
  function carregando(btn, texto) {
    if (!btn || btn.getAttribute('aria-busy') === 'true') return function () {};
    var original = btn.textContent, estavaDesabilitado = btn.disabled;
    btn.disabled = true;
    btn.setAttribute('aria-busy', 'true');
    if (texto) btn.textContent = texto;
    return function () {
      btn.removeAttribute('aria-busy');
      btn.textContent = original;
      btn.disabled = estavaDesabilitado;
    };
  }
  window.Botao = { carregando: carregando };
})();
