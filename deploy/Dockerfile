# Site estático (front) servido pelo Caddy. O build por ambiente roda aqui dentro,
# então a imagem nunca carrega a CSP/config de desenvolvimento.
FROM alpine:3.20 AS build
RUN apk add --no-cache bash grep sed
WORKDIR /src
COPY frontend ./frontend
COPY deploy/build-front.sh ./deploy/build-front.sh
# Vazio = API na mesma origem (Caddy encaminha /api). Ou https://api.seudominio.com
ARG API_ORIGIN=
RUN API_ORIGIN="$API_ORIGIN" bash deploy/build-front.sh prod

FROM caddy:2
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /src/build/front-prod /srv
