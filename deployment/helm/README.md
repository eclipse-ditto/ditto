# Eclipse Ditto :: Helm

This folder contains a Helm chart which can be used to install Eclipse Ditto
with its backing Database - MongoDB - and a reverse proxy - nginx - in front of the HTTP and WebSocket API.

The Chart [README.md](eclipse-ditto/README.md) contains some more usage instructions.

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

## Usage From Helm Hub

A version of the Chart is also available via [Helm Hub](https://hub.helm.sh/charts/kiwigrid/ditto-digital-twins).
Just follow the usage instructions described there.

## Local Setup

### Requirements

- [Kubernetes IN Dokcer](https://github.com/kubernetes-sigs/kind)
- [kubectl](https://kubernetes.io/docs/tasks/kubectl/install/)
- [Helm v2](https://docs.helm.sh/using_helm/#installing-helm)

### Run Eclipse Ditto

#### Start KIND Cluster

Prepare a kind configuration file named `kind-config.yaml`, with following content:

```yaml
kind: Cluster
apiVersion: kind.sigs.k8s.io/v1alpha3
nodes:
# the control plane node config
- role: control-plane
# Worker reachable from local machine
- role: worker
  extraPortMappings:
  # HTTP
  - containerPort: 32080
    hostPort: 80
```

Start kind cluster

```bash
kind create cluster --image "kindest/node:v1.14.9" --config kind-config.yaml
```

#### Install Eclipse Ditto

**Note:** Following commands requires Helm v2

Prepare Tiller

```bash
cd <DITTO_PATH>/deployment/helm/
helm dependency update ./eclipse-ditto/

kubectl --namespace kube-system create serviceaccount tiller
kubectl create clusterrolebinding tiller-cluster-rule --clusterrole=cluster-admin --serviceaccount=kube-system:tiller
helm init --service-account tiller --upgrade --wait
```

Install eclipse-ditto chart with default configuration

```bash
helm upgrade eclipse-ditto ./eclipse-ditto --install --wait
```

Follow the instructions from `NOTES.txt` (printed when install is finished).

#### Delete Eclipse Ditto Release

```bash
helm delete eclipse-ditto --purge
```

#### Destroy KIND Cluster

```bash
kind delete cluster
```

### Troubleshooting

If you experience high resource consumption (either CPU or RAM or both), you can limit the resource usage by specifing resource limits.
This can be done individually for each single component.
Here is an example how to limit CPU to 0.25 Cores and RAM to 512MiB for the `connectivity` service:

```bash
helm upgrade eclipse-ditto ./eclipse-ditto --install --wait --set connectivity.resources.limits.cpu=0.25 --set connectivity.resources.limits.memory=512Mi
```
