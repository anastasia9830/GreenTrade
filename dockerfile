FROM gradle:8.7-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle clean installDist --no-daemon

# ---- Run stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# (build/install/<имя>)
COPY --from=build /app/build/install/GreenTrade/ /app/

# Переменные окружения для подключения к БД (compose их задаёт)
ENV DB_URL= DB_USER= DB_PASS=
# Gradle Application plugin (bin/<имя-проекта>)
CMD ["/app/bin/GreenTrade"]
