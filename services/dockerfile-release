FROM adoptopenjdk/openjdk11-openj9:x86_64-alpine-jdk-11.0.5_10_openj9-0.17.0-slim

ARG MAVEN_REPO=https://repo.eclipse.org/content/repositories/ditto/org/eclipse/ditto/
ARG SERVICE_STARTER
ARG SERVICE_VERSION

ENV HTTP_PORT=8080 \
    HOSTING_ENVIRONMENT=Docker \
    DITTO_HOME=/opt/ditto

# Http port
EXPOSE 8080

RUN set -x \
    && apk upgrade --update-cache \
    && apk add --no-cache tini openssl \
    && apk add --no-cache --virtual .tmp-deps wget ca-certificates shadow \
    && mkdir -p $DITTO_HOME \
    && groupadd --system ditto \
    && useradd --no-log-init --system --home-dir $DITTO_HOME --shell /bin/sh --gid ditto --uid 1000 ditto \
    && cd $DITTO_HOME \
    && wget -q --show-progress -O ${SERVICE_STARTER}-${SERVICE_VERSION}-allinone.jar ${MAVEN_REPO}${SERVICE_STARTER}/${SERVICE_VERSION}/${SERVICE_STARTER}-${SERVICE_VERSION}-allinone.jar \
    && ln -s ${SERVICE_STARTER}-${SERVICE_VERSION}-allinone.jar starter.jar \
    && chown -R ditto:ditto $DITTO_HOME \
    && apk del .tmp-deps

USER ditto
WORKDIR $DITTO_HOME
ENTRYPOINT ["/sbin/tini", "--"]
CMD ["java", "-jar", "/opt/ditto/starter.jar"]
