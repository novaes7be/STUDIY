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
  var PASSO = 60;            // ms entre quadros do scramble
  var TICKS_POR_LETRA = 2;   // ~120 ms por letra revelada
  var TROCA = 0.3;           // chance de uma letra cifrada mudar de símbolo a cada quadro
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

  /* ---------- data-scramble ----------
     A fonte é proporcional: trocar "I" por "@" alarga a palavra, muda a quebra de linha e o
     tamanho do bloco. Por isso, antes de animar, cada letra vira uma caixa com a largura REAL
     que ela ocupa (medida com Range, já com kerning e letter-spacing) e cada palavra fica sem
     quebra interna. Os símbolos trocam dentro das caixas; o layout não se mexe. No fim, os
     nós de texto originais voltam no lugar das caixas. */
  function textos(el) {
    var lista = [], w = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
    while (w.nextNode()) if (w.currentNode.nodeValue.trim()) lista.push(w.currentNode);
    return lista;
  }

  function larguras(no) {
    var r = document.createRange(), s = no.nodeValue, ws = [];
    for (var k = 0; k < s.length; k++) {
      r.setStart(no, k); r.setEnd(no, k + 1);
      var a = r.getBoundingClientRect(), w = a.width;
      if (k + 1 < s.length) {  // avanço até a próxima letra, se ela estiver na mesma linha
        r.setStart(no, k + 1); r.setEnd(no, k + 2);
        var b = r.getBoundingClientRect();
        if (Math.abs(b.top - a.top) < 1 && b.left > a.left) w = b.left - a.left;
      }
      ws.push(w);
    }
    return ws;
  }

  function travar(no) {
    var s = no.nodeValue, ws = larguras(no), caixas = [];
    var envelope = document.createElement('span'), palavra = null;
    envelope.className = 'sc';
    for (var k = 0; k < s.length; k++) {
      var c = s[k];
      if (/\s/.test(c)) { envelope.append(c); palavra = null; continue; }
      if (!palavra) { palavra = document.createElement('span'); palavra.className = 'sc-w'; envelope.append(palavra); }
      var caixa = document.createElement('span');
      caixa.className = 'sc-c';
      caixa.style.width = ws[k].toFixed(2) + 'px';
      caixa.textContent = c;
      palavra.append(caixa);
      caixas.push({ el: caixa, original: c, cifra: sorteia() });
    }
    no.replaceWith(envelope);
    return { no: no, envelope: envelope, caixas: caixas };
  }

  function sorteia() { return CHARS[(Math.random() * CHARS.length) | 0]; }

  /* Suave: uma letra se revela a cada TICKS_POR_LETRA passos, e cada letra ainda
     cifrada só troca de símbolo com chance TROCA por passo (em vez de piscar sempre). */
  function embaralhar(el) {
    if (reduz || el._scrambling) return;
    el._scrambling = true;
    var partes = textos(el).map(travar);
    var caixas = [].concat.apply([], partes.map(function (p) { return p.caixas; }));
    var passo = 0;
    var timer = setInterval(function () {
      var revelados = Math.floor(passo / TICKS_POR_LETRA);
      caixas.forEach(function (c, i) {
        if (i < revelados) { c.el.textContent = c.original; return; }
        if (Math.random() < TROCA) c.cifra = sorteia();
        c.el.textContent = c.cifra;
      });
      if (++passo > caixas.length * TICKS_POR_LETRA) {
        clearInterval(timer);
        partes.forEach(function (p) { p.envelope.replaceWith(p.no); });
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

  /* ---------- Brilho do cursor (só no fundo; css/motion.css .cursor-glow) ----------
     Só com mouse/trackpad e sem "reduzir movimento". O brilho persegue o ponteiro com
     atraso (interpolação de 12% por quadro) e o loop para quando alcança o alvo. */
  function brilhoDoCursor() {
    if (reduz || !window.matchMedia('(hover: hover) and (pointer: fine)').matches) return;
    var raiz = document.documentElement;
    var camada = document.createElement('div');
    camada.className = 'cursor-glow';
    camada.setAttribute('aria-hidden', 'true');
    document.body.prepend(camada);
    var alvo = { x: innerWidth / 2, y: innerHeight / 3 }, atual = { x: alvo.x, y: alvo.y }, rodando = false;
    function quadro() {
      atual.x += (alvo.x - atual.x) * .12;
      atual.y += (alvo.y - atual.y) * .12;
      raiz.style.setProperty('--mx', atual.x.toFixed(1) + 'px');
      raiz.style.setProperty('--my', atual.y.toFixed(1) + 'px');
      if (Math.abs(alvo.x - atual.x) + Math.abs(alvo.y - atual.y) > .5) requestAnimationFrame(quadro);
      else rodando = false;
    }
    addEventListener('pointermove', function (e) {
      alvo.x = e.clientX; alvo.y = e.clientY;
      camada.classList.add('on');
      if (!rodando) { rodando = true; requestAnimationFrame(quadro); }
    }, { passive: true });
    document.addEventListener('pointerleave', function () { camada.classList.remove('on'); });
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
  function iniciar() { observar(); brilhoDoCursor(); }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', iniciar);
  else iniciar();
})();
