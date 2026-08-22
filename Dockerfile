# Etapa 1 — compila o JAR dentro da propria imagem, para o build nao depender
# do que esta instalado na maquina de quem roda.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# As dependencias sao baixadas antes do codigo: enquanto o pom.xml nao mudar,
# o Docker reaproveita esta camada e o build fica bem mais rapido.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# Etapa 2 — imagem final: so o runtime Java e o JAR, sem Maven e sem codigo-fonte.
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
