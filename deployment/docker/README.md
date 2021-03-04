## Eclipse Ditto :: Docker

This folder contains an example `docker-compose.yml` which can be used to start Eclipse Ditto 
with its backing Database - MongoDB - and a reverse proxy - nginx - in front of the HTTP and WebSocket API.

## Configure nginx
The nginx's configuration is located in the `nginx.conf` file and contains a "Basic authentication" 
for accessing the HTTP and WebSocket API. The users for this sample authentication are configured 
in the `nginx.httpasswd` file also located in this directory.

In order to add a new entry to this file, use the "openssl passwd" tool to create a hashed password:
```bash
openssl passwd -quiet
 Password: <enter password>
 Verifying - Password: <enter password>
```

Append the printed hash in the `nginx.httpasswd` file placing the username who shall receive this 
password in front like this:
```
ditto:A6BgmB8IEtPTs
```

## Configuration of the services

You may configure each service via passing variables to the java VM in the entrypoint section for each
service.

```yml
...
# Alternative approach for configuration of the service
command: java -Dditto.gateway.authentication.devops.password=foobar -jar starter.jar
```

To get a list of available configuration options you may retrieve them from a running instance via:

```bash
# Substitute gateway with the service you are interested in
curl http://devops:foobar@localhost:8080/devops/config/gateway/?path=ditto
```

Or by going through the configuration files in this repository e.g. [/services/gateway/starter/src/main/resources/gateway.conf](https://github.com/eclipse/ditto/blob/master/services/gateway/starter/src/main/resources/gateway.conf).

## Start Eclipse Ditto

```bash
docker-compose up -d
```

Check the logs after starting up:
```bash
docker-compose logs -f
```

## Stop Eclipse Ditto

```bash
docker-compose down
```
