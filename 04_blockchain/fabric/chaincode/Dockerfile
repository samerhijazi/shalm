FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /build/target/fabric-chaincode.jar app.jar
ENV CHAINCODE_SERVER_PORT=9999
EXPOSE 9999
CMD ["java", "-jar", "app.jar"]
