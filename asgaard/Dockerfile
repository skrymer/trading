FROM node:24-alpine AS build
RUN corepack enable && corepack prepare pnpm@10.24.0 --activate
WORKDIR /app
COPY package.json pnpm-lock.yaml .npmrc ./
RUN pnpm install --frozen-lockfile
COPY . .
ENV NUXT_BACKEND_URL=http://udgaard:8080
RUN pnpm postinstall && pnpm build

FROM node:24-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /app/.output .output
USER app
EXPOSE 3000
CMD ["node", ".output/server/index.mjs"]
