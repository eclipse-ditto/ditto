## Eclipse Ditto :: k3s

This folder contains example yaml files which can be used to start Eclipse Ditto 
with its backing Database - MongoDB - and a reverse proxy - nginx - in front of the HTTP and WebSocket API.

## Requirements
* [k3s](https://rancher.com/docs/k3s/latest/en/)
* Port 30080 needs to be available on the node

## Install k3s
Run the following command to install k3s. 
```bash
curl -sfL https://get.k3s.io | sh -
```

Change the owner of `/etc/rancher/k3s/k3s.yaml` to your user.
```bash
sudo chown <groupId>:<userId> /etc/rancher/k3s/k3s.yaml
```

Copy k3s kube config to .kube directory:
```bash
cp /etc/rancher/k3s/k3s.yaml .kube/config
```

Verify that the `kubectl` command works:
```bash
kubectl get nodes
```

The command should print something like this:
```bash
kubectl get nodes
NAME              STATUS   ROLES                  AGE     VERSION
<your-hostname>   Ready    control-plane,master   5h21m   v1.20.2+k3s1
```

The container logs can be found here: `/var/log/containers/...`

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
password in front of Ditto like this:
```
ditto:A6BgmB8IEtPTs
```

## Start Eclipse Ditto

### Create configuration mappings
```bash
kubectl create configmap nginx-conf --from-file=deployment/kubernetes/deploymentFiles/nginx/nginx.conf
kubectl create configmap nginx-cors --from-file=deployment/kubernetes/deploymentFiles/nginx/nginx-cors.conf
kubectl create configmap nginx-htpasswd --from-file=deployment/kubernetes/deploymentFiles/nginx/nginx.htpasswd
kubectl create configmap nginx-index --from-file=deployment/kubernetes/deploymentFiles/nginx/index.html
kubectl create configmap swagger-ui-api --from-file=$PWD/documentation/src/main/resources/openapi
```

#### MongoDB
There are two ways starting a MongoDB instance.
Either use a simple MongoDB container without persistence.
```bash
kubectl apply -f deployment/kubernetes/deploymentFiles/mongodb/mongodb.yaml
```

Or use the stateful MongoDB set with a local persistent volume.
Before running the following commands be sure that the `/data/db/` directory is existing.
```bash
kubectl apply -f deployment/kubernetes/deploymentFiles/mongodb-statefulset/storage-class.yaml
envsubst < deployment/kubernetes/deploymentFiles/mongodb-statefulset/persistent-volume.yaml | kubectl apply -f -
kubectl apply -f deployment/kubernetes/deploymentFiles/mongodb-statefulset/mongodb-statefulset.yaml
```

##### Dedicated MongoDB
In case you already have a MongoDB in the cloud or elsewhere it is possible to connect Ditto to this MongoDB. 
This can be done by setting the MongoDB URI via env variable "MONGO_DB_URI" in the 
`deployment/kubernetes/deploymentFiles/ditto/ditto-cluster.yml` for all services except the `gateway`.
Other MongoDB settings can be set via env variables and are documented in
[Operating Ditto](https://www.eclipse.org/ditto/installation-operating.html) section.

### Start Eclipse Ditto

Ditto uses the `latest` tag for its images. If you want to use a different version replace the `latest` tag in
`deployment/kubernetes/deploymentFiles/ditto/ditto-cluster.yml` with the version you want to use.

```bash
kubectl apply -f deployment/kubernetes/deploymentFiles/ditto/
# Start ditto services with an alternative version e.g. 0-SNAPSHOT
# cat deployment/kubernetes/ditto/ditto-cluster.yaml | sed s/latest/0-SNAPSHOT/ | kubectl apply -f -
```

#### Start Swagger UI
```bash
kubectl apply -f deployment/kubernetes/deploymentFiles/swagger/swagger.yaml
```

#### Start Reverse Proxy (nginx)
```bash
kubectl apply -f deployment/kubernetes/deploymentFiles/nginx/nginx.yaml
```

#### Verify all pods are running
Run the following command to verify that everything is running.

```bash
kubectl get pods
NAME                             READY   STATUS    RESTARTS   AGE
mongodb-0                        1/1     Running   0          5m
policies-5d6798cc6-dzklx         1/1     Running   0          3m
gateway-d9f9cbb65-4fsbk          1/1     Running   0          3m
things-search-768c894bd4-v4n2z   1/1     Running   0          3m
things-5787ffdf7f-mn2cs          1/1     Running   0          3m
connectivity-54b9799b8f-496f5    1/1     Running   0          3m
concierge-7b8fd6f857-6585g       1/1     Running   0          3m
swagger-b8asd6f857-651bg         1/1     Running   0          2m
nginx-7bdb84f965-gf2lp           1/1     Running   0          1m
```

### Ditto up & running
Now Ditto should be up & running. You can access Ditto on the local port *30080*.


#### Ditto status/health endpoint
To check if Ditto is up & running you can use the Ditto health endpoint.
curl -u devops --request GET localhost:30080/status/health
Default devops password is: "foobar"

Have Fun!
