/* Tema claro/escuro. Carregado no <head> (sem defer) para não piscar na troca de página.
   A preferência de tema é só visual: não tem relação com permissões. */
(function () {
  var KEY = 'studiy-theme';
  var tema = null;
  try { tema = localStorage.getItem(KEY); } catch (e) { /* storage bloqueado */ }
  if (tema !== 'dark' && tema !== 'light') {
    tema = window.matchMedia && matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  document.documentElement.dataset.theme = tema;
  document.documentElement.classList.add('js'); // habilita animações de entrada (sem JS o conteúdo fica visível)

  document.addEventListener('DOMContentLoaded', function () {
    var botao = document.getElementById('themeToggle');
    if (!botao) return;
    botao.addEventListener('click', function () {
      var novo = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
      document.documentElement.dataset.theme = novo;
      try { localStorage.setItem(KEY, novo); } catch (e) { /* ignora */ }
    });
  });
})();
