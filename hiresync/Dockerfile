# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm install

COPY . .
RUN npm run build -- --configuration production

# ── Stage 2: Runtime (Nginx) ─────────────────────────────────────────────────
FROM nginx:1.31-alpine
COPY --from=build /app/dist/hiresync/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
