/* Configuração do front. NÃO edite a URL da API aqui: deploy/build-front.sh troca __API_BASE__
   pelo valor do ambiente (dev: http://localhost:8080; prod: vazio = mesma origem via Caddy, ou https://...).
   Aberto sem build, cai na mesma origem. */
(function () {
  var base = '__API_BASE__';

  window.STUDIY = {
    API_BASE: base.indexOf('__') === 0 ? '' : base,
    // Chaves da paleta da matéria (o servidor só aceita estas). As CORES ficam em css/tokens.css ([data-subject] e .swatch[data-k]).
    SUBJECTS: [
      { key: 'verde', label: 'Verde' },
      { key: 'azul', label: 'Azul' },
      { key: 'roxo', label: 'Roxo' },
      { key: 'vermelho', label: 'Rosa' },
      { key: 'grafite', label: 'Grafite' }
    ],
    DEFAULT_SUBJECT: 'verde'
  };
})();
