# Eclipse Ditto :: Helm

This folder contains a example helm chart which can be used to start Eclipse Ditto
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

```text
ditto:A6BgmB8IEtPTs
```

## Requirements

- [Minikube](https://github.com/kubernetes/minikube/)
- [kubectl](https://kubernetes.io/docs/tasks/kubectl/install/)
- [VirtualBox](https://www.virtualbox.org/wiki/Downloads)
- [Helm](https://docs.helm.sh/using_helm/#installing-helm)

## Start Eclipse Ditto

### Start Minikube and install Helm into one node cluster

```bash
minikube start
helm init
```

### Start Eclipse Ditto

```bash
cd <DITTO_PATH>/deployment/helm/
helm update dependencies
helm install --name ditto ./eclipse-ditto/
```

### Use Eclipse Ditto

```bash
minikube service ditto
```

### Use Minikube Dashboard

```bash
minikube dashboard
```

### Delete Eclipse Ditto release

```bash
cd <DITTO_PATH>/deployment/helm/
helm delete --purge ditto
```
