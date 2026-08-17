# ---------- Build Stage ----------
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn -B -q -e -C -DskipTests dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

# ---------- Runtime Stage ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN addgroup --system spring && adduser --system spring --ingroup spring

COPY --from=builder /app/target/*.jar app.jar

RUN mkdir -p /srv/fleetovo-files
RUN chown -R spring:spring /srv/fleetovo-files

USER spring

EXPOSE 8443

ENTRYPOINT ["java","-jar","/app/app.jar"]
