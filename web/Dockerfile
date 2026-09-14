FROM node:24-alpine AS build
WORKDIR /app
COPY web/package*.json ./
RUN npm ci
COPY web/ .
COPY LICENSE NOTICE /
ARG VITE_OIDC_URL=http://localhost:8081
ARG VITE_OIDC_REALM=signet-campus
ARG VITE_OIDC_CLIENT=campus-dev
RUN npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine
COPY web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
