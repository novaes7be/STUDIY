#!/usr/bin/env node
// Gera as páginas traduzidas do front. Chamado por deploy/build-front.sh com a pasta do build:
//   node deploy/i18n-front.mjs build/front-prod
//
// O português fica no próprio HTML (a página funciona aberta sem build). Para cada dicionário
// frontend/i18n/<idioma>.json, cria <build>/<idioma>/*.html com os textos trocados:
//   data-i18n="chave"                          troca o conteúdo do elemento (pode ter HTML simples)
//   data-i18n-attr="aria-label:chave; alt:c2"  troca atributos do próprio elemento
//   <!--idiomas-->                             vira os links para a mesma página nos outros idiomas
// Chave faltando em qualquer idioma derruba o build: nada sai com texto pela metade.
import { readFileSync, writeFileSync, readdirSync, mkdirSync, rmSync } from 'node:fs';
import { join } from 'node:path';

const destino = process.argv[2];
if (!destino) { console.error('Uso: node deploy/i18n-front.mjs <pasta do build>'); process.exit(1); }

const PADRAO = { codigo: 'pt-BR', pasta: '', nome: 'Português' };
const NOMES = { en: 'English', es: 'Español' };
const pastaI18n = join(destino, 'i18n');
const idiomas = readdirSync(pastaI18n).filter((f) => f.endsWith('.json')).map((f) => f.slice(0, -5)).sort();
const dicionarios = Object.fromEntries(
  idiomas.map((l) => [l, JSON.parse(readFileSync(join(pastaI18n, `${l}.json`), 'utf8'))]));
const paginas = readdirSync(destino).filter((f) => f.endsWith('.html'));
const erros = [];
const usadas = new Set();

/** Troca o conteúdo de cada elemento com data-i18n. O elemento não pode ter outro igual dentro. */
function trocarConteudos(html, traduzir, pagina) {
  const abertura = /<([a-zA-Z][a-zA-Z0-9]*)\b[^>]*\sdata-i18n="([^"]+)"[^>]*>/g;
  let saida = '';
  let pos = 0;
  let m;
  while ((m = abertura.exec(html))) {
    const [tagInteira, tag, chave] = m;
    const inicio = m.index + tagInteira.length;
    const fim = html.indexOf(`</${tag}>`, inicio);
    if (fim < 0 || html.slice(inicio, fim).includes(`<${tag}`)) {
      erros.push(`${pagina}: <${tag} data-i18n="${chave}"> sem fechamento ou com <${tag}> dentro`);
      continue;
    }
    saida += html.slice(pos, inicio) + traduzir(chave);
    pos = fim;
    abertura.lastIndex = fim;
  }
  return saida + html.slice(pos);
}

/** Troca os atributos listados em data-i18n-attr="atributo:chave; ...". */
function trocarAtributos(html, traduzir, pagina) {
  return html.replace(/<[a-zA-Z][^>]*\sdata-i18n-attr="([^"]+)"[^>]*>/g, (tag, lista) => {
    for (const par of lista.split(';').map((p) => p.trim()).filter(Boolean)) {
      const [atributo, chave] = par.split(':').map((p) => p.trim());
      const re = new RegExp(`(\\s${atributo}=")[^"]*(")`);
      if (!re.test(tag)) { erros.push(`${pagina}: atributo ${atributo} não existe para a chave ${chave}`); continue; }
      tag = tag.replace(re, (_, a, b) => a + traduzir(chave).replace(/"/g, '&quot;') + b);
    }
    return tag;
  });
}

/** Links para esta mesma página em cada idioma, menos o atual. */
function linksIdiomas(pagina, atual) {
  return [PADRAO, ...idiomas.map((l) => ({ codigo: l, pasta: `${l}/`, nome: NOMES[l] ?? l }))]
    .filter((i) => i.codigo !== atual)
    .map((i) => `<a href="/${i.pasta}${pagina}" hreflang="${i.codigo}" lang="${i.codigo}">${i.nome}</a>`)
    .join('');
}

for (const pagina of paginas) {
  const original = readFileSync(join(destino, pagina), 'utf8');
  for (const idioma of idiomas) {
    const dic = dicionarios[idioma];
    const traduzir = (chave) => {
      usadas.add(chave);
      if (typeof dic[chave] !== 'string') { erros.push(`${idioma}.json: falta "${chave}" (${pagina})`); return ''; }
      return dic[chave];
    };
    let html = trocarAtributos(trocarConteudos(original, traduzir, pagina), traduzir, pagina);
    html = html
      .replace(/<html lang="[^"]*"/, `<html lang="${idioma}"`)
      // A página fica um nível abaixo: arquivos estáticos voltam para a raiz; links entre páginas ficam no idioma.
      .replace(/(\s(?:href|src)=")(css|js|fonts)\//g, '$1../$2/')
      .replace('<!--idiomas-->', linksIdiomas(pagina, idioma));
    mkdirSync(join(destino, idioma), { recursive: true });
    writeFileSync(join(destino, idioma, pagina), html);
  }
  writeFileSync(join(destino, pagina), original.replace('<!--idiomas-->', linksIdiomas(pagina, PADRAO.codigo)));
}

for (const idioma of idiomas) {
  for (const chave of Object.keys(dicionarios[idioma])) {
    if (!usadas.has(chave)) console.warn(`aviso: ${idioma}.json tem "${chave}", que nenhuma página usa`);
  }
}
// Os dicionários já foram aplicados: não vão para o site publicado.
rmSync(pastaI18n, { recursive: true });

if (erros.length) {
  console.error(`ERRO: tradução incompleta (${erros.length}):\n - ${[...new Set(erros)].join('\n - ')}`);
  process.exit(1);
}
console.log(`i18n: ${paginas.length} páginas em ${idiomas.join(', ')}`);
