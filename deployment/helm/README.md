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
kubectl create namespace dittons
helm dependency update ./eclipse-ditto/
```

(Optional) we recommend to define a K8s [PersistentVolumeClaim](https://kubernetes.io/docs/concepts/storage/persistent-volumes/) independent of the Helm release to ensure the data survives a helm delete:

```bash
kubectl apply -f ditto-mongodb-pvc.yaml
```

...in this case start Ditto with:

```bash
helm upgrade ditto ./eclipse-ditto/ --set mongodb.persistence.enabled=true,mongodb.persistence.existingClaim=ditto-mongodb-pvc --wait --install
```

.. or else

```bash
helm upgrade ditto ./eclipse-ditto/ --set mongodb.persistence.enabled=true --wait --install
```

...or without persistence for the MongoDB at all:

```bash
helm upgrade ditto ./eclipse-ditto/ --wait --install
```

### Use Eclipse Ditto

```bash
minikube service -n dittons ditto
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
