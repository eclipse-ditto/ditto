## Eclipse Ditto :: Kubernetes

This folder contains an example yaml files which can be used to start Eclipse Ditto 
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
[Minikube](https://github.com/kubernetes/minikube/)
[kubectl](https://kubernetes.io/docs/tasks/kubectl/install/)
[VirtualBox](https://www.virtualbox.org/wiki/Downloads)

## Start Eclipse Ditto

### Start Minikube
```bash
minikube start # --docker-env HTTP_PROXY=http://<your-proxy>:<port> --docker-env HTTPS_PROXY=http://<your-proxy>:<port>
```

If you are developing behind a corporate proxy append your corporate certificates after minikube startup to 
/etc/ssl/certs/ca-certificates and restart docker daemon.  
 
### Create configuration mappings
Use the appropriate local path to your installation.
```bash
kubectl create configmap nginx-conf --from-file=$HOME/ditto/kubernetes/nginx.conf
kubectl create configmap nginx-cors --from-file=$HOME/ditto/kubernetes/nginx-cors.conf
kubectl create configmap nginx-htpasswd --from-file=$HOME/ditto/kubernetes/nginx.htpasswd
kubectl create configmap nginx-index --from-file=$HOME/repos/ditto/kubernetes/index.html
kubectl create configmap swagger-ui-api --from-file=$HOME/ditto/documentation/src/main/resources/openapi
```

### Start Eclipse Ditto
```bash
kubectl create -f .
```

### Use Eclipse Ditto
```bash
minikube service ditto
```

### Use Minikube Dashboard
```bash
minikube dashboard
```


Have Fun!