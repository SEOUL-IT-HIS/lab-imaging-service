FROM eclipse-temurin:17-jre

RUN mkdir /app
WORKDIR /app

ADD ./build/libs/*.jar /app/app.jar

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]