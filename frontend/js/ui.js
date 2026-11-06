/* Interações visuais: navbar (destaque que desliza, seção ativa, encolhe no scroll, menu mobile),
   animação de entrada das seções e brilho que segue o cursor nos botões. Sem dependências. */
(function () {
  'use strict';
  var reduz = window.matchMedia && matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* Brilho do botão acompanha o mouse (delegação: vale para botões criados depois) */
  document.addEventListener('pointermove', function (ev) {
    var b = ev.target.closest && ev.target.closest('.btn');
    if (!b) return;
    var r = b.getBoundingClientRect();
    b.style.setProperty('--mx', (ev.clientX - r.left) + 'px');
    b.style.setProperty('--my', (ev.clientY - r.top) + 'px');
  }, { passive: true });

  /* Entrada suave das seções */
  var alvos = document.querySelectorAll('.reveal');
  if (alvos.length) {
    if ('IntersectionObserver' in window && !reduz) {
      var io = new IntersectionObserver(function (items) {
        items.forEach(function (i) { if (i.isIntersecting) { i.target.classList.add('in'); io.unobserve(i.target); } });
      }, { threshold: 0.12 });
      alvos.forEach(function (el) { io.observe(el); });
    } else { alvos.forEach(function (el) { el.classList.add('in'); }); }
  }

  /* Navbar */
  var nav = document.querySelector('.nav');
  if (!nav) return;

  /* Já entrou? O botão "Entrar" vira "Meu painel" */
  var logado = false;
  try { logado = sessionStorage.getItem('studiy-logado') === '1'; } catch (e) { /* ignora */ }
  var botaoEntrar = nav.querySelector('.nav-end a[href="login.html"]');
  if (logado && botaoEntrar) { botaoEntrar.textContent = 'Meu painel'; botaoEntrar.setAttribute('href', 'dashboard.html'); }
  var links = Array.prototype.slice.call(nav.querySelectorAll('.nav-links a[href^="#"]'));
  var glow = nav.querySelector('.nav-glow');
  var lista = nav.querySelector('.nav-links');
  var burger = nav.querySelector('.nav-burger');
  var ativo = null;

  function moverGlow(link) {
    if (!link || !glow) { if (glow) glow.style.opacity = '0'; return; }
    glow.style.width = link.offsetWidth + 'px';
    glow.style.transform = 'translateX(' + link.offsetLeft + 'px)';
    glow.style.opacity = '1';
  }
  function marcar(link) {
    ativo = link;
    links.forEach(function (l) { l.setAttribute('aria-current', l === link ? 'true' : 'false'); });
    moverGlow(link);
  }
  links.forEach(function (l) {
    l.addEventListener('mouseenter', function () { moverGlow(l); });
    l.addEventListener('focus', function () { moverGlow(l); });
    l.addEventListener('click', function () { lista.classList.remove('open'); if (burger) burger.setAttribute('aria-expanded', 'false'); });
  });
  lista.addEventListener('mouseleave', function () { moverGlow(ativo); });

  /* Seção ativa conforme a rolagem */
  var secoes = links.map(function (l) { return document.querySelector(l.getAttribute('href')); });
  function espiar() {
    nav.classList.toggle('scrolled', window.scrollY > 30);
    var marca = window.innerHeight * 0.35, escolhido = null;
    secoes.forEach(function (s, i) { if (s && s.getBoundingClientRect().top <= marca) escolhido = links[i]; });
    if (escolhido !== ativo) marcar(escolhido);
  }
  window.addEventListener('scroll', espiar, { passive: true });
  window.addEventListener('resize', function () { moverGlow(ativo); });
  espiar();

  /* Menu mobile */
  if (burger) {
    burger.addEventListener('click', function () {
      var abrir = burger.getAttribute('aria-expanded') !== 'true';
      burger.setAttribute('aria-expanded', String(abrir));
      lista.classList.toggle('open', abrir);
    });
  }
})();