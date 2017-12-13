FROM openjdk:8-jdk-alpine

ARG MAVEN_REPO=https://repo.eclipse.org/content/repositories/ditto/org/eclipse/ditto/
ARG SERVICE_STARTER
ARG SERVICE_VERSION

ENV HTTP_PORT=8080 \
    HOSTING_ENVIRONMENT=Docker \
    DITTO_HOME=/opt/ditto

# Http port
EXPOSE 8080

RUN set -x \
    && apk add --no-cache wget \
    && mkdir -p $DITTO_HOME \
    && cd $DITTO_HOME \
    && wget -O ${SERVICE_STARTER}-${SERVICE_VERSION}-allinone.jar --no-verbose ${MAVEN_REPO}${SERVICE_STARTER}/${SERVICE_VERSION}/${SERVICE_STARTER}-${SERVICE_VERSION}-allinone.jar \
    && ln -s ${SERVICE_STARTER}-${SERVICE_VERSION}-allinone.jar starter.jar

WORKDIR $DITTO_HOME
ENTRYPOINT ["java","-jar","starter.jar"]
