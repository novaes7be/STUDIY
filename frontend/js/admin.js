/* Painel admin (somente leitura). Quem decide o acesso é o servidor: ROLE_ADMIN só existe para
   papel ADMIN + MFA ativo + sessão verificada por TOTP. Aqui só exibimos o que a API devolve,
   sempre com textContent. */
(function () {
  'use strict';
  var $ = function (s) { return document.querySelector(s); };
  var estado = { aba: 'usuarios', pagina: 0, mfaAtivo: false };

  function msg(texto, ok) {
    var m = $('#msg');
    m.textContent = texto;
    m.className = 'msg ' + (ok ? 'info' : 'error');
    m.hidden = !texto;
  }
  function dataHora(iso) {
    if (!iso) return '';
    var d = new Date(iso);
    return isNaN(d) ? '' : d.toLocaleString('pt-BR');
  }
  function limpar(el) { while (el.firstChild) el.removeChild(el.firstChild); }

  var COLUNAS = {
    usuarios: [['id', 'ID'], ['nome', 'Nome'], ['email', 'E-mail'], ['papel', 'Papel'], ['plano', 'Plano'],
      ['emailVerificado', 'E-mail ok'], ['mfaAtivo', 'MFA'], ['bloqueado', 'Bloqueado'], ['criadoEm', 'Criado em']],
    pedidos: [['id', 'Pedido'], ['usuarioEmail', 'Usuário'], ['metodo', 'Método'], ['status', 'Status'],
      ['valor', 'Valor'], ['criadoEm', 'Criado em'], ['pagoEm', 'Pago em']],
    'uso-ia': [['dia', 'Dia'], ['chamadas', 'Chamadas'], ['falhas', 'Falhas'], ['tokensEntrada', 'Tokens entrada'],
      ['tokensSaida', 'Tokens saída'], ['custo', 'Custo']]
  };

  function valor(item, campo) {
    var v = item[campo];
    if (typeof v === 'boolean') return v ? 'sim' : 'não';
    if (/Em$/.test(campo)) return dataHora(v);
    return v === null || v === undefined ? '' : String(v);
  }

  function tabela(colunas, itens) {
    var t = $('#tabela');
    limpar(t);
    var cab = document.createElement('tr');
    colunas.forEach(function (c) { var th = document.createElement('th'); th.textContent = c[1]; cab.append(th); });
    var thead = document.createElement('thead'); thead.append(cab); t.append(thead);
    var corpo = document.createElement('tbody');
    itens.forEach(function (it) {
      var tr = document.createElement('tr');
      colunas.forEach(function (c) { var td = document.createElement('td'); td.textContent = valor(it, c[0]); tr.append(td); });
      corpo.append(tr);
    });
    t.append(corpo);
  }

  async function carregar() {
    msg('');
    var paginado = estado.aba !== 'uso-ia';
    var r = await api.get('/api/admin/' + estado.aba + (paginado ? '?pagina=' + estado.pagina : ''));
    $('#dados').hidden = false;
    $('#mfa').hidden = true;
    document.querySelectorAll('[data-aba]').forEach(function (b) { b.setAttribute('aria-pressed', String(b.dataset.aba === estado.aba)); });
    if (paginado) {
      tabela(COLUNAS[estado.aba], r.itens);
      $('#resumo').textContent = r.total + ' registro(s)';
      $('#paginaInfo').textContent = 'Página ' + (r.pagina + 1) + ' de ' + Math.max(1, r.totalPaginas);
      $('#anterior').disabled = r.pagina <= 0;
      $('#proxima').disabled = r.pagina + 1 >= r.totalPaginas;
    } else {
      tabela(COLUNAS['uso-ia'], r.porDia);
      $('#resumo').textContent = 'Últimos ' + r.dias + ' dias: ' + r.chamadasTotal + ' chamada(s), ' + r.custoTotal
        + (r.maioresConsumidores.length ? ' · Maior consumo: ' + r.maioresConsumidores.map(function (c) { return c.email + ' (' + c.custo + ')'; }).join(', ') : '');
      $('#paginaInfo').textContent = '';
      $('#anterior').disabled = true; $('#proxima').disabled = true;
    }
  }

  async function pedirMfa() {
    $('#mfa').hidden = false;
    $('#dados').hidden = true;
    if (estado.mfaAtivo) {
      $('#mfaTexto').textContent = 'Digite o código do seu app autenticador para abrir o painel.';
      return;
    }
    $('#mfaTexto').textContent = 'Primeiro acesso de administrador: configure o segundo fator.';
    try {
      var cfg = await api.post('/api/auth/mfa/configurar');
      $('#mfaSegredo').textContent = cfg.segredo;
      $('#mfaUri').textContent = cfg.uri;
      $('#mfaSetup').hidden = false;
    } catch (e) { msg(e.message); }
  }

  $('#mfaForm').addEventListener('submit', async function (ev) {
    ev.preventDefault();
    var rota = estado.mfaAtivo ? 'verificar' : 'ativar';
    try {
      await api.post('/api/auth/mfa/' + rota, { codigo: $('#codigo').value.trim() });
      estado.mfaAtivo = true;
      $('#codigo').value = '';
      limpar($('#mfaSegredo')); limpar($('#mfaUri')); $('#mfaSetup').hidden = true;
      await carregar();
    } catch (e) { msg(e.message); }
  });
  document.querySelectorAll('[data-aba]').forEach(function (b) {
    b.addEventListener('click', function () { estado.aba = b.dataset.aba; estado.pagina = 0; carregar().catch(function (e) { msg(e.message); }); });
  });
  $('#anterior').addEventListener('click', function () { estado.pagina--; carregar().catch(function (e) { msg(e.message); }); });
  $('#proxima').addEventListener('click', function () { estado.pagina++; carregar().catch(function (e) { msg(e.message); }); });

  (async function iniciar() {
    var perfil;
    try { perfil = await api.get('/api/usuarios/me'); } catch (e) { msg(e.message); return; }
    if (!perfil.admin) { msg('Acesso restrito a administradores.'); return; }
    estado.mfaAtivo = perfil.mfaAtivo;
    try { await carregar(); }
    catch (e) { if (e.status === 403) pedirMfa(); else msg(e.message); }
  })();
})();
