## Eclipse Ditto :: Minikube 

Running Eclipse Ditto on minikube with kubernetes descriptor files or Ditto Helm chart.

In the following sections you can find a short description how to run Eclipse Ditto on minikube.

## Requirements

* [Minikube](https://github.com/kubernetes/minikube/)
* [kubectl](https://kubernetes.io/docs/tasks/kubectl/install/)

## Start Minikube

Start Minikube with one of the provided drivers.
```bash
minikube start 

# verify minikube is started
minikube status
```  

## Start Eclipse Ditto

Currently, there are to possibilities to start Eclipse Ditto inside your minikube cluster.
Either use the files in the [deploymentFiles](../deploymentFiles) directory and the [Readme](../README.md)
or use the [Ditto Helm chart](../../helm/README.md).

### Ditto up & running

Now Ditto should be up & running.

### Use Eclipse Ditto

```bash
minikube service ditto
```

### Use Minikube Dashboard

```bash
minikube dashboard
```


Have Fun!
