FROM mozilla/sbt:latest AS build

RUN mkdir /build

WORKDIR /build

COPY build.sbt /build/
COPY project /build/project/

RUN sbt -no-colors update

ADD . /build/

RUN sbt -no-colors dist \
 && unzip target/universal/website-*.zip

FROM adoptopenjdk/openjdk15:x86_64-alpine-jre-15.0.1_9@sha256:de9de89d6dd7324c78efa243038dcd55d5b2d3be326fc3c09068c10bfcf4b573

RUN apk add --update --no-cache curl

RUN adduser -S play \
 && mkdir /app \
 && chown play /app

USER play

WORKDIR /app

COPY --from=build "/build/website-*/bin" /app/bin/
COPY --from=build "/build/website-*/lib" /app/lib/
COPY --from=build "/build/website-*/conf" /app/conf/

EXPOSE 9000/tcp

HEALTHCHECK --interval=20s --timeout=3s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:9000/ || exit 1

ENTRYPOINT ["./bin/website"]