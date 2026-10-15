FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY src src
RUN mkdir /classes && javac --release 21 --add-modules jdk.httpserver,jdk.jfr -d /classes src/main/java/dev/jvmlab/*.java && cp src/main/resources/index.html /classes/
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /classes /app/classes
RUN mkdir /artifacts && chown 10001:10001 /artifacts
USER 10001:10001
ENTRYPOINT ["java", "--add-modules", "jdk.httpserver,jdk.jfr", "-cp", "/app/classes", "dev.jvmlab.Main"]
CMD ["run", "compare", "/artifacts"]
