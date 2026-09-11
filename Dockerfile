FROM eclipse-temurin:17-jre

RUN mkdir /app
WORKDIR /app

ADD ./build/libs/*.jar /app/app.jar

EXPOSE 38088
entrypoint ["java", "-jar", "app.jar", "--server.port=38088"]