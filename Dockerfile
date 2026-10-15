FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY target/tf-user.jar /app/app.jar
EXPOSE 8101
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
