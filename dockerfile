FROM gradle:8.7-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle clean installDist --no-daemon


FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/build/install/GreenTrade/ /app/

ENV DB_URL= DB_USER= DB_PASS=

CMD ["/app/bin/GreenTrade"]
