/* Movimento declarativo por atributos (estilos em css/motion.css). Só apresentação.
   data-reveal            entra com fade + subida ao aparecer na tela (data-reveal="120" = atraso em ms)
   data-scramble          texto "descriptografa" letra a letra ao carregar e no hover
   data-count             número sobe de 0 até o valor ao aparecer (data-count="1200" ou o próprio texto)
   Conteúdo inserido depois (ex.: dashboard): chame Motion.observar(elemento). */
(function () {
  'use strict';
  var reduz = window.matchMedia && matchMedia('(prefers-reduced-motion: reduce)').matches;
  var temIO = 'IntersectionObserver' in window;
  var CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ#%&$@01';
  var PASSO = 45;
  var fmt = new Intl.NumberFormat('pt-BR');

  if (!reduz && temIO) document.documentElement.classList.add('motion');

  /* ---------- data-reveal ---------- */
  var ioReveal = temIO && !reduz ? new IntersectionObserver(function (itens) {
    itens.forEach(function (i) {
      if (!i.isIntersecting) return;
      i.target.classList.add('is-in');
      ioReveal.unobserve(i.target);
    });
  }, { threshold: .12 }) : null;

  function reveal(el) {
    var atraso = parseInt(el.getAttribute('data-reveal'), 10);
    if (atraso > 0) el.style.setProperty('--reveal-delay', atraso + 'ms');
    if (ioReveal) ioReveal.observe(el); else el.classList.add('is-in');
  }

  /* ---------- data-scramble ---------- */
  function textos(el) {
    var lista = [], w = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
    while (w.nextNode()) lista.push({ no: w.currentNode, original: w.currentNode.nodeValue });
    return lista;
  }

  function embaralhar(el) {
    if (reduz || el._scrambling) return;
    el._scrambling = true;
    var partes = el._partes || (el._partes = textos(el));
    var total = partes.reduce(function (s, p) { return s + p.original.length; }, 0);
    var revelados = 0;
    var timer = setInterval(function () {
      var resta = revelados;
      partes.forEach(function (p) {
        var s = '';
        for (var k = 0; k < p.original.length; k++) {
          var c = p.original[k];
          s += (resta-- > 0 || c === ' ' || c === '\n') ? c : CHARS[(Math.random() * CHARS.length) | 0];
        }
        p.no.nodeValue = s;
      });
      if (++revelados > total) {
        clearInterval(timer);
        partes.forEach(function (p) { p.no.nodeValue = p.original; });
        el._scrambling = false;
      }
    }, PASSO);
  }

  function scramble(el) {
    // Leitores de tela leem o texto final, não as letras trocando
    if (!el.hasAttribute('aria-label')) el.setAttribute('aria-label', el.textContent.trim());
    embaralhar(el);
    el.addEventListener('mouseenter', function () { embaralhar(el); });
  }

  /* ---------- data-count ---------- */
  var ioCount = temIO ? new IntersectionObserver(function (itens) {
    itens.forEach(function (i) {
      if (!i.isIntersecting) return;
      contar(i.target);
      ioCount.unobserve(i.target);
    });
  }, { threshold: .4 }) : null;

  function alvo(el) {
    var v = el.getAttribute('data-count');
    var n = parseFloat(v !== '' && v != null ? v : el.textContent.replace(/\./g, '').replace(',', '.'));
    return isNaN(n) ? null : n;
  }

  function contar(el) {
    var fim = alvo(el);
    if (fim === null) return;
    if (reduz) { el.textContent = fmt.format(fim); return; }
    var inicio = el._valor || 0, t0 = performance.now(), dur = 900;
    (function quadro(t) {
      var p = Math.min(1, (t - t0) / dur), e = 1 - Math.pow(1 - p, 3);
      el.textContent = fmt.format(Math.round(inicio + (fim - inicio) * e));
      if (p < 1) requestAnimationFrame(quadro); else el._valor = fim;
    })(t0);
  }

  function count(el) {
    if (ioCount) ioCount.observe(el); else contar(el);
  }

  /* ---------- API ---------- */
  function observar(raiz) {
    raiz = raiz || document;
    raiz.querySelectorAll('[data-reveal]:not([data-m])').forEach(function (el) { el.setAttribute('data-m', ''); reveal(el); });
    raiz.querySelectorAll('[data-scramble]:not([data-m])').forEach(function (el) { el.setAttribute('data-m', ''); scramble(el); });
    raiz.querySelectorAll('[data-count]').forEach(function (el) {
      // Já observado e o valor mudou (ex.: XP depois de revisar): anima do valor anterior
      if (el.hasAttribute('data-m')) contar(el); else { el.setAttribute('data-m', ''); count(el); }
    });
  }

  window.Motion = { observar: observar, contar: contar, embaralhar: embaralhar };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', function () { observar(); });
  else observar();
})();
