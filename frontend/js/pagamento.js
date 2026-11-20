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
      $('#priceDays').textContent = t('pag.por_dias', '/ {n} dias', { n: c.dias });
      $('#daysText').textContent = c.dias;
    } catch (e) { /* mantém o texto padrão */ }
  }

  $('#payForm').addEventListener('submit', async function (ev) {
    ev.preventDefault();
    $('#payMsg').hidden = true;
    var metodo = (document.querySelector('input[name="metodo"]:checked') || {}).value;
    if (!metodo) { msg(t('pag.escolha', 'Escolha cartão ou Pix.')); return; }
    var botao = $('#payForm button[type="submit"]');
    botao.disabled = true;
    botao.setAttribute('aria-busy', 'true'); // spinner (css/buttons.css)
    try {
      // Preço, plano e endereço de volta são decididos pelo servidor; aqui só vai o método.
      var r = await api.post('/api/pagamentos/checkout', { metodo: metodo });
      var destino = new URL(r.url);
      if (destino.protocol !== 'https:' && destino.protocol !== 'http:') throw new Error(t('pag.endereco_invalido', 'Endereço de pagamento inválido.'));
      window.location.assign(destino.href);
    } catch (e) {
      msg(e.message || t('pag.erro_iniciar', 'Não foi possível iniciar o pagamento.'));
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
      catch (e) { mostrarStatus(t('pag.erro_verificar', 'Não foi possível verificar'), e.message, false); return; }

      if (p.status === 'PAGO') {
        var ate = p.planoExpiraEm ? new Date(p.planoExpiraEm).toLocaleDateString(window.IDIOMA) : '';
        mostrarStatus(t('pag.confirmado', 'Pagamento confirmado!'), ate
          ? t('pag.ativo_ate', 'Seu plano Pro está ativo até {data}. Bons estudos!', { data: ate })
          : t('pag.ativo', 'Seu plano Pro está ativo. Bons estudos!'), true);
        $('#simBox').hidden = true; $('#toPanel').hidden = false; $('#retry').hidden = true; $('#again').hidden = true;
        return;
      }
      if (p.status === 'REJEITADO') {
        mostrarStatus(t('pag.nao_aprovado', 'O pagamento não foi aprovado'), t('pag.nao_aprovado_txt', 'Nada foi cobrado. Você pode tentar de novo com outro método.'), false);
        $('#simBox').hidden = true; $('#retry').hidden = false; $('#again').hidden = true;
        return;
      }
      if (p.status === 'REEMBOLSADO') {
        mostrarStatus(t('pag.reembolsado', 'Pagamento reembolsado'), t('pag.reembolsado_txt', 'Este pedido foi reembolsado e o acesso Pro correspondente foi encerrado.'), false);
        $('#simBox').hidden = true;
        return;
      }
      // PENDENTE
      $('#simBox').hidden = !simulado;
      tentativas++;
      if (tentativas < 40) {
        mostrarStatus(t('pag.aguardando', 'Aguardando a confirmação…'), p.metodo === 'PIX'
          ? t('pag.aguardando_pix', 'Se você já pagou o Pix, a confirmação costuma levar alguns segundos.')
          : t('pag.aguardando_cartao', 'Assim que o pagamento for aprovado, o Pro é liberado automaticamente.'), false);
        setTimeout(verificar, 3000);
      } else {
        mostrarStatus(t('pag.sem_confirmacao', 'Ainda não recebemos a confirmação'), t('pag.sem_confirmacao_txt', 'Você pode fechar esta página: quando o pagamento for aprovado, o Pro é liberado na sua conta.'), false);
        $('#again').hidden = false;
      }
    }

    $('#again').addEventListener('click', function () { $('#again').hidden = true; tentativas = 0; verificar(); });
    $('#simBtn').addEventListener('click', async function () {
      this.disabled = true;
      this.setAttribute('aria-busy', 'true');
      try { await api.post('/api/pagamentos/' + encodeURIComponent(pedidoId) + '/simular'); }
      catch (e) { mostrarStatus(t('pag.erro_simular', 'Não foi possível simular'), e.message, false); }
      verificar();
    });
    verificar();
  }

  carregarConfig();
  if (pedidoId) acompanhar();
})();
