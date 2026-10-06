# syntax=docker/dockerfile:1
# Builds every runnable part of the PoC inside Docker, so running the app only needs Docker installed.
# docker-compose.yml picks a target per service; the shared "build" stage runs once and is cached.

# --- Java build (backend services only; the JavaFX desktop client is built on the host) ---------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests package -pl server-auth,service-user,service-notification,service-product -am

FROM eclipse-temurin:17-jre AS java-runtime
WORKDIR /app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM java-runtime AS server-auth
COPY --from=build /src/server-auth/target/server-auth.jar app.jar

FROM java-runtime AS service-user
COPY --from=build /src/service-user/target/service-user.jar app.jar

FROM java-runtime AS service-notification
COPY --from=build /src/service-notification/target/service-notification.jar app.jar

FROM java-runtime AS service-product
COPY --from=build /src/service-product/target/service-product.jar app.jar

# --- Mobile web app (static files) ---------------------------------------------------------------
FROM nginx:1.27-alpine AS web-mobile
COPY web-mobile/nginx.conf /etc/nginx/conf.d/default.conf
COPY web-mobile/ /usr/share/nginx/html/

# --- API tests (Postman collection run by Newman); also fills the app with test data -------------
FROM postman/newman:6-alpine AS api-tests
WORKDIR /etc/newman
COPY docs/api/community-store-poc.postman_collection.json collection.json
ENTRYPOINT ["newman", "run", "collection.json", "--color", "on", \
    "--env-var", "gateway_auth=http://server-auth:9600/auth", \
    "--env-var", "gateway_user=http://service-user:9601/service-user", \
    "--env-var", "gateway_notification=http://service-notification:9602/service-notification", \
    "--env-var", "gateway_product=http://service-product:9603/service-product"]
