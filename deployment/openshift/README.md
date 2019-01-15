## Eclipse Ditto :: OpenShift

This folder contains example yaml files which can be used to start Eclipse Ditto 
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

## Requirements
Running OpenShift cluster with at least developer access.
* [oc](https://docs.openshift.com/container-platform/latest/cli_reference/get_started_cli.html)

## Start Eclipse Ditto

### Apply the pod reader role 
This is necessary for the pods to access the Kubernetes API and then build the akka cluster.
```bash
cd <DITTO_PATH>
oc apply -f pod-reader-role.yaml
```

### Create MongoDB secrets from literals
```bash
oc create secret generic mongodb-secret -n digitaltwins --from-literal=MONGODB_PASSWORD='mongodb-secret-pw' --from-literal=MONGODB_ADMIN_PASSWORD='mongodb-admin-secret-pw' &&
oc create secret generic mongodb-uri -n digitaltwins --from-literal=mongodb-uri='mongodb://mongodb-user:mongodb-secret-pw@mongodb:27017/iot-things?ssl=false'
```

### Create configuration mappings
```bash
oc create configmap nginx-conf --from-file=nginx/nginx.conf &&
oc create configmap nginx-cors --from-file=nginx/nginx-cors.conf &&
oc create configmap nginx-htpasswd --from-file=nginx/nginx.htpasswd &&
oc create configmap nginx-index --from-file=nginx/index.html
```

### Start Eclipse Ditto

#### Start MongoDB
```bash
oc apply -f mongodb/mongodb.yaml
```

#### Start Ditto services
```bash
oc apply -f ditto/ditto-cluster.yaml
# Start ditto services with an alternative version e.g. 0-SNAPSHOT
# cat openshift/ditto/ditto-cluster.yaml | sed s/latest/0-SNAPSHOT/ | oc apply -f -
```

#### Start Reverse Proxy (nginx)
```bash
oc apply -f nginx/nginx.yaml
```

Have Fun!
