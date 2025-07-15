## Eclipse Ditto :: Docker

This folder contains an example `docker-compose.yml` which can be used to start Eclipse Ditto 
with its backing Database - MongoDB - and a reverse proxy - nginx - in front of the HTTP and WebSocket API.


## Resource requirements

For a "single instance" setup on a local machine you need at least:
* 2 CPU cores which can be used by Docker
* 4 GB of RAM which can be used by Docker

## Configure nginx

The nginx's configuration is located in the `nginx.conf` file and contains a "Basic authentication" 
for accessing the HTTP and WebSocket API. The users for this sample authentication are configured 
in the `nginx.htpasswd` file also located in this directory.

In order to add a new entry to this file, use the "openssl passwd" tool to create a hashed password:
```bash
openssl passwd -quiet
 Password: <enter password>
 Verifying - Password: <enter password>
```

Append the printed hash in the `nginx.htpasswd` file placing the username who shall receive this 
password in front like this:
```
ditto:A6BgmB8IEtPTs
```

## Configuration of the services

You may configure each service via passing system properties via the `JAVA_TOOL_OPTIONS` environment variable in the 
entrypoint section for each service.

```yml
...
# Alternative approach for configuration of the service
environment:
  - JAVA_TOOL_OPTIONS=-Dditto.gateway.authentication.devops.password=foobar
```

To get a list of available configuration options you may retrieve them from a running instance via:

```bash
# Substitute gateway with the service you are interested in
curl http://devops:foobar@localhost:8080/devops/config/gateway/?path=ditto
```

Or by going through the configuration files in this repository, all available configuration files are 
[linked here](https://www.eclipse.dev/ditto/installation-operating.html#ditto-configuration).

## Start Eclipse Ditto

```bash
docker-compose up -d
```
If you want to install a specific Ditto version please use the DITTO_VERSION var from the docker-compose.yaml file before running docker compose:
```bash
export DITTO_VERSION=<dockerhub-image-tag>
```
Check the logs after starting up:
```bash
docker-compose logs -f
```

Check the resource consumption in order to find out if you e.g. require more memory:
```bash
docker stats
```

## Stop Eclipse Ditto

```bash
docker-compose down
```
