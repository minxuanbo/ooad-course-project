FROM node:24.15.0-alpine AS build
WORKDIR /build
COPY web/package*.json ./
RUN npm ci
COPY web/ ./
ARG IDENTITY_PUBLIC_URL
RUN test -n "$IDENTITY_PUBLIC_URL"
ENV VITE_API_BASE_URL=/api
ENV VITE_MOCK_ENABLED=false
ENV VITE_IDENTITY_BASE_URL=${IDENTITY_PUBLIC_URL}
RUN npm run build

FROM nginx:1.28-alpine
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
