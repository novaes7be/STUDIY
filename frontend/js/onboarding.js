/* Onboarding do primeiro acesso: 3 passos, aparece uma vez por navegador. */
(function () {
  'use strict';
  var KEY = 'studiy_onboarding_v1';
  try { if (localStorage.getItem(KEY)) return; } catch (e) { return; }

  var passos = [
    ['Crie uma matéria', 'Clique em “Nova matéria” e escolha uma cor para ela.'],
    ['Envie um PDF', 'Use “Enviar PDF” com o material da aula (texto selecionável, até 10 MB).'],
    ['Gere e revise', 'A IA cria o resumo e os flashcards. Revise os cartões no dia certo.']
  ];

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text) n.textContent = text;
    return n;
  }

  var anterior = document.activeElement;
  var back = el('div', 'onb-back');
  var box = el('div', 'onb');
  box.setAttribute('role', 'dialog');
  box.setAttribute('aria-modal', 'true');
  box.setAttribute('aria-labelledby', 'onbTitulo');

  var titulo = el('h2', '', 'Bem-vindo ao Studiy');
  titulo.id = 'onbTitulo';
  box.appendChild(titulo);
  box.appendChild(el('p', '', 'Em 3 passos você já estuda com ajuda da IA:'));

  var lista = el('ol');
  passos.forEach(function (p) {
    var li = el('li');
    var txt = el('div');
    txt.appendChild(el('strong', '', p[0]));
    txt.appendChild(el('span', '', p[1]));
    li.appendChild(txt);
    lista.appendChild(li);
  });
  box.appendChild(lista);

  var acoes = el('div', 'onb-actions');
  var ok = el('button', 'btn btn-grad', 'Entendi, vamos lá');
  ok.type = 'button';
  acoes.appendChild(ok);
  box.appendChild(acoes);
  back.appendChild(box);

  function fechar() {
    try { localStorage.setItem(KEY, '1'); } catch (e) { /* sem armazenamento: aparece de novo, sem problema */ }
    document.removeEventListener('keydown', teclado);
    back.remove();
    if (anterior && anterior.focus) anterior.focus();
  }
  function teclado(ev) {
    if (ev.key === 'Escape') fechar();
    if (ev.key === 'Tab') { ev.preventDefault(); ok.focus(); } // mantém o foco dentro do diálogo
  }
  ok.addEventListener('click', fechar);
  back.addEventListener('click', function (ev) { if (ev.target === back) fechar(); });
  document.addEventListener('keydown', teclado);
  document.body.appendChild(back);
  ok.focus();
})();
