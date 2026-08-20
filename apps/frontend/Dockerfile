FROM node:22-alpine AS build

RUN corepack enable

WORKDIR /workspace
COPY . .
RUN pnpm install --frozen-lockfile
RUN pnpm --filter @hwalro/frontend build

FROM nginx:alpine

ENV NGINX_ENVSUBST_FILTER="AUTH_SERVICE_URL|SIMULATION_SERVICE_URL|REGULATION_SERVICE_URL"

COPY apps/frontend/nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /workspace/apps/frontend/dist /usr/share/nginx/html

EXPOSE 80
