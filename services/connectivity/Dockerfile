# This Dockerfile is used on Docker Hub for "autobuild" of the Eclipse Ditto gateway service
FROM openjdk:8-jdk-alpine

ENV HTTP_PORT=8080 \
    HOSTING_ENVIRONMENT=Docker \
    DITTO_HOME=/opt/ditto \
    SERVICE_STARTER=ditto-services-connectivity-starter

# Http port
EXPOSE 8080

RUN set -x \
    && apk add --no-cache gnupg unzip \
    && apk add --no-cache libressl wget \
    && gpg --keyserver pgp.mit.edu --recv-keys 83152fb003d7a0d6 \

    && mkdir -p $DITTO_HOME \
    && cd $DITTO_HOME \
    && wget -O $SERVICE_STARTER-$CACHE_TAG-allinone.jar --no-verbose http://repo1.maven.org/maven2/org/eclipse/ditto/$SERVICE_STARTER/$CACHE_TAG/$SERVICE_STARTER-$CACHE_TAG-allinone.jar \
    && wget -O $SERVICE_STARTER-$CACHE_TAG-allinone.jar.asc --no-verbose http://repo1.maven.org/maven2/org/eclipse/ditto/$SERVICE_STARTER/$CACHE_TAG/$SERVICE_STARTER-$CACHE_TAG-allinone.jar.asc \
    && gpg --batch --verify $SERVICE_STARTER-$CACHE_TAG-allinone.jar.asc $SERVICE_STARTER-$CACHE_TAG-allinone.jar
    && ln -s $SERVICE_STARTER-$CACHE_TAG-allinone.jar starter.jar

WORKDIR $DITTO_HOME
ENTRYPOINT ["java","-jar","starter.jar"]
