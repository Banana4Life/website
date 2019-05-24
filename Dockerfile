FROM mozilla/sbt:latest AS build

RUN mkdir /build

WORKDIR /build

ADD . /build/

RUN sbt dist \
 && unzip target/universal/website-*.zip

FROM openjdk:12

RUN useradd -r play \
 && mkdir /app \
 && chown play /app

USER play

WORKDIR /app

COPY --from=build "/build/website-*/bin" /app/bin/
COPY --from=build "/build/website-*/lib" /app/lib/
COPY --from=build "/build/website-*/conf" /app/conf/

EXPOSE 9000/tcp

ENTRYPOINT ["./bin/mailmanager", "-Dplay.server.pidfile.path=/dev/null"]