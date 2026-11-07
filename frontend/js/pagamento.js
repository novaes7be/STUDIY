/* Assinatura do Pro: escolher cartão ou Pix, ir ao checkout e acompanhar a confirmação.
   O preço e o plano são decididos pelo servidor; aqui só escolhemos o método. */
(function () {
  'use strict';

  var $ = function (s) { return document.querySelector(s); };
  var params = new URLSearchParams(window.location.search);
  var pedidoId = params.get('pedido');
  var simulado = params.get('simulado') === '1';

  function msg(texto) { var m = $('#payMsg'); m.textContent = texto; m.hidden = false; }

  /* ---------- Escolha do método ---------- */
  async function carregarConfig() {
    try {
      var c = await api.get('/api/pagamentos/config');
      $('#priceValue').textContent = c.precoFormatado;
      $('#priceDays').textContent = '/ ' + c.dias + ' dias';
      $('#daysText').textContent = c.dias;
    } catch (e) { /* mantém o texto padrão */ }
  }

  $('#payForm').addEventListener('submit', async function (ev) {
    ev.preventDefault();
    $('#payMsg').hidden = true;
    var metodo = (document.querySelector('input[name="metodo"]:checked') || {}).value;
    if (!metodo) { msg('Escolha cartão ou Pix.'); return; }
    var botao = $('#payForm button[type="submit"]');
    botao.disabled = true;
    botao.setAttribute('aria-busy', 'true'); // spinner (css/buttons.css)
    try {
      // Preço, plano e endereço de volta são decididos pelo servidor; aqui só vai o método.
      var r = await api.post('/api/pagamentos/checkout', { metodo: metodo });
      var destino = new URL(r.url);
      if (destino.protocol !== 'https:' && destino.protocol !== 'http:') throw new Error('Endereço de pagamento inválido.');
      window.location.assign(destino.href);
    } catch (e) {
      msg(e.message || 'Não foi possível iniciar o pagamento.');
      botao.disabled = false;
      botao.removeAttribute('aria-busy');
    }
  });

  /* ---------- Acompanhar o pedido ---------- */
  function mostrarStatus(titulo, texto, ok) {
    $('#statusTitle').textContent = titulo;
    $('#statusText').textContent = texto;
    $('#statusCard').classList.toggle('status-ok', !!ok);
  }

  async function acompanhar() {
    $('#viewPlano').hidden = true;
    $('#viewStatus').hidden = false;
    var tentativas = 0;

    async function verificar() {
      var p;
      try { p = await api.get('/api/pagamentos/' + encodeURIComponent(pedidoId)); }
      catch (e) { mostrarStatus('Não foi possível verificar', e.message, false); return; }

      if (p.status === 'PAGO') {
        var ate = p.planoExpiraEm ? new Date(p.planoExpiraEm).toLocaleDateString('pt-BR') : '';
        mostrarStatus('Pagamento confirmado!', 'Seu plano Pro está ativo' + (ate ? ' até ' + ate : '') + '. Bons estudos!', true);
        $('#simBox').hidden = true; $('#toPanel').hidden = false; $('#retry').hidden = true; $('#again').hidden = true;
        return;
      }
      if (p.status === 'REJEITADO') {
        mostrarStatus('O pagamento não foi aprovado', 'Nada foi cobrado. Você pode tentar de novo com outro método.', false);
        $('#simBox').hidden = true; $('#retry').hidden = false; $('#again').hidden = true;
        return;
      }
      if (p.status === 'REEMBOLSADO') {
        mostrarStatus('Pagamento reembolsado', 'Este pedido foi reembolsado e o acesso Pro correspondente foi encerrado.', false);
        $('#simBox').hidden = true;
        return;
      }
      // PENDENTE
      $('#simBox').hidden = !simulado;
      tentativas++;
      if (tentativas < 40) {
        mostrarStatus('Aguardando a confirmação…', p.metodo === 'PIX'
          ? 'Se você já pagou o Pix, a confirmação costuma levar alguns segundos.'
          : 'Assim que o pagamento for aprovado, o Pro é liberado automaticamente.', false);
        setTimeout(verificar, 3000);
      } else {
        mostrarStatus('Ainda não recebemos a confirmação', 'Você pode fechar esta página: quando o pagamento for aprovado, o Pro é liberado na sua conta.', false);
        $('#again').hidden = false;
      }
    }

    $('#again').addEventListener('click', function () { $('#again').hidden = true; tentativas = 0; verificar(); });
    $('#simBtn').addEventListener('click', async function () {
      this.disabled = true;
      this.setAttribute('aria-busy', 'true');
      try { await api.post('/api/pagamentos/' + encodeURIComponent(pedidoId) + '/simular'); }
      catch (e) { mostrarStatus('Não foi possível simular', e.message, false); }
      verificar();
    });
    verificar();
  }

  carregarConfig();
  if (pedidoId) acompanhar();
})();
