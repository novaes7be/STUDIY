/* Sugestão de idioma: na versão em português, se o navegador prefere um idioma que o site tem,
   mostra um aviso discreto com o link para a mesma página nesse idioma. Nunca redireciona sozinho:
   quem escolheu ver em português (ou fechou o aviso) continua em português. */
(function () {
  var DISPONIVEIS = ['en', 'es'];
  var TEXTOS = {
    en: { msg: 'This page is available in English.', ir: 'Switch to English', fechar: 'Close' },
    es: { msg: 'Esta página está disponible en español.', ir: 'Ver en español', fechar: 'Cerrar' }
  };
  var CHAVE = 'studiy.idioma.aviso-fechado';

  /**
   * Decide qual idioma sugerir, ou null para não mostrar nada.
   * @param {string[]} preferidos  navigator.languages, na ordem de preferência (ex.: ['es-AR', 'es', 'en-US'])
   * @param {string[]} disponiveis idiomas que o site tem além do português (ex.: ['en', 'es'])
   * @returns {string|null}        um item de `disponiveis`, ou null
   */
  function idiomaSugerido(preferidos, disponiveis) {
    // TODO(human)
    return null;
  }

  function lerFechado() { try { return localStorage.getItem(CHAVE) === '1'; } catch (e) { return false; } }
  function gravarFechado() { try { localStorage.setItem(CHAVE, '1'); } catch (e) { /* sem storage: só some agora */ } }

  if (document.documentElement.lang !== 'pt-BR' || lerFechado()) return;
  var idioma = idiomaSugerido(navigator.languages || [navigator.language || ''], DISPONIVEIS);
  if (!idioma || DISPONIVEIS.indexOf(idioma) < 0) return;

  var t = TEXTOS[idioma];
  var pagina = location.pathname.split('/').pop() || 'index.html';
  var aviso = document.createElement('div');
  aviso.className = 'aviso-idioma';
  aviso.setAttribute('role', 'region');
  aviso.setAttribute('lang', idioma);
  aviso.setAttribute('aria-label', t.msg);
  var msg = document.createElement('span');
  msg.textContent = t.msg;
  var ir = document.createElement('a');
  ir.href = '/' + idioma + '/' + pagina;
  ir.hreflang = idioma;
  ir.textContent = t.ir;
  var fechar = document.createElement('button');
  fechar.type = 'button';
  fechar.className = 'icon-btn';
  fechar.setAttribute('aria-label', t.fechar);
  fechar.textContent = '×';
  fechar.addEventListener('click', function () { gravarFechado(); aviso.remove(); });
  aviso.append(msg, ir, fechar);
  document.body.prepend(aviso);
})();
