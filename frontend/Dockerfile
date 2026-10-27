# Multi-stage build for SpendSense Angular Frontend
FROM node:20-alpine AS builder

WORKDIR /app

# Cache dependencies
COPY package*.json ./
RUN npm ci

# Copy sources and build production bundle
COPY . .
RUN npm run build -- --configuration=production

# Nginx web server runtime
FROM nginx:alpine

COPY --from=builder /app/dist/frontend /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
