/* Configuração do front.
   Em desenvolvimento a API é detectada sozinha: mesma máquina da página, porta 8080
   (assim http://127.0.0.1:5500 fala com http://127.0.0.1:8080 e localhost com localhost).
   Em produção, troque API_PRODUCAO pelo domínio HTTPS da API e ajuste o connect-src da CSP nos HTML. */
(function () {
  var host = window.location.hostname;
  var local = host === '' || host === 'localhost' || host === '127.0.0.1';
  var API_PRODUCAO = 'https://api.seudominio.com.br';

  window.STUDIY = {
    API_BASE: local ? 'http://' + (host || 'localhost') + ':8080' : API_PRODUCAO,
    // Chaves da paleta da matéria. As CORES ficam em css/tokens.css ([data-subject="..."]).
    SUBJECTS: [
      { key: 'azul', label: 'Azul' },
      { key: 'roxo', label: 'Roxo' },
      { key: 'vermelho', label: 'Vermelho' },
      { key: 'grafite', label: 'Preto' }
    ],
    DEFAULT_SUBJECT: 'azul'
  };
})();
