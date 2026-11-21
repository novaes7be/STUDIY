/* Onboarding do primeiro acesso: 3 passos. QUEM decide se aparece é o backend (perfil.onboardingPendente);
   o dashboard chama window.studiyOnboarding(aoConcluir) e, ao fechar, avisa a API. */
(function () {
  'use strict';

  var passos = [
    [t('onb.p1_t', 'Crie uma matéria'), t('onb.p1', 'Clique em “Nova matéria” e escolha uma cor para ela.')],
    [t('onb.p2_t', 'Envie um PDF'), t('onb.p2', 'Use “Enviar PDF” com o material da aula (texto selecionável, até 10 MB).')],
    [t('onb.p3_t', 'Gere e revise'), t('onb.p3', 'A IA cria o resumo e os flashcards. Revise os cartões no dia certo.')]
  ];

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text) n.textContent = text;
    return n;
  }

  window.studiyOnboarding = function (aoConcluir) {
  var anterior = document.activeElement;
  var back = el('div', 'onb-back');
  var box = el('div', 'onb');
  box.setAttribute('role', 'dialog');
  box.setAttribute('aria-modal', 'true');
  box.setAttribute('aria-labelledby', 'onbTitulo');

  var titulo = el('h2', '', t('onb.titulo', 'Bem-vindo ao Studiy'));
  titulo.id = 'onbTitulo';
  box.appendChild(titulo);
  box.appendChild(el('p', '', t('onb.sub', 'Em 3 passos você já estuda com ajuda da IA:')));

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
  var ok = el('button', 'btn btn-accent', t('onb.ok', 'Entendi, vamos lá'));
  ok.type = 'button';
  acoes.appendChild(ok);
  box.appendChild(acoes);
  back.appendChild(box);

  function fechar() {
    if (aoConcluir) aoConcluir();
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
  };
})();
