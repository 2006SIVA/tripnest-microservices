FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend/ ./
ARG MODULE
RUN mvn -B -pl ${MODULE} -am package -DskipTests
FROM eclipse-temurin:21-jre
ARG MODULE
COPY --from=build /src/${MODULE}/target/${MODULE}-1.0.0.jar /app.jar
EXPOSE 8080
USER 10001:10001
ENTRYPOINT ["java","-jar","/app.jar"]
