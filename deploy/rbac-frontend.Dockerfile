FROM node:24.15.0-alpine AS build
WORKDIR /build
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM nginx:1.28-alpine
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
