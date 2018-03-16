FROM adoptopenjdk/openjdk8-openj9:jdk8u162-b12_openj9-0.8.0-alpine

ARG MAVEN_REPO=https://repo.eclipse.org/content/repositories/ditto/org/eclipse/ditto/
ARG SERVICE_STARTER
ARG SERVICE_VERSION

ENV HTTP_PORT=8080 \
    HOSTING_ENVIRONMENT=Docker \
    DITTO_HOME=/opt/ditto

# Http port
EXPOSE 8080

RUN set -x \
    && apk upgrade libssl1.0 --update-cache \
    && apk add --no-cache --virtual .tmp-deps wget ca-certificates \
    && mkdir -p $DITTO_HOME \
    && cd $DITTO_HOME \
    && wget -q --show-progress -O ${SERVICE_STARTER}-${SERVICE_VERSION}-allinone.jar ${MAVEN_REPO}${SERVICE_STARTER}/${SERVICE_VERSION}/${SERVICE_STARTER}-${SERVICE_VERSION}-allinone.jar \
    && ln -s ${SERVICE_STARTER}-${SERVICE_VERSION}-allinone.jar starter.jar \
    && apk del .tmp-deps

WORKDIR $DITTO_HOME
ENTRYPOINT ["java","-jar","starter.jar"]
