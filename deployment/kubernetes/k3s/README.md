## Eclipse Ditto :: k3s

Running Eclipse Ditto on k3s with kubernetes descriptor files or Ditto Helm chart.

In the following sections you can find a short description how to set up k3s and run Eclipse Ditto.

## Requirements

* [k3s](https://rancher.com/docs/k3s/latest/en/)
* Port 30080 needs to be available on the node

## Install k3s

Run the following command to install k3s. 
```bash
curl -sfL https://get.k3s.io | sh -
```

Change the owner of `/etc/rancher/k3s/k3s.yaml` to your group and user.
```bash
sudo chown <groupId>:<userId> /etc/rancher/k3s/k3s.yaml
```

Copy k3s kube config to local .kube directory as `config` file (backup existing kubectl `config` if you have one).
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

## Start Eclipse Ditto

Currently, there are to possibilities to start Eclipse Ditto inside your minikube cluster.
Either use the files in the [deploymentFiles](../deploymentFiles) directory and the [Readme](../README.md) 
or use the [Ditto Helm chart](../../helm/README.md).

### Ditto up & running

Now Ditto should be up & running. You can access Ditto on the local port *30080*.

#### Ditto status/health endpoint

To check if Ditto is up & running you can use the Ditto health endpoint.

    curl -u devops --request GET localhost:30080/status/health

Default devops password is: "foobar"


Have Fun!
