# Eclipse Ditto :: Helm

The Ditto Helm chart sources are managed here.

## Install Ditto via Helm Chart

To install the chart with the release name eclipse-ditto, run the following commands:

```shell script
helm repo add eclipse-iot https://www.eclipse.org/packages/charts/
helm repo update
helm install eclipse-ditto eclipse-iot/ditto
```

# Uninstall the Helm Chart

To uninstall/delete the eclipse-ditto deployment:

```shell script
helm delete eclipse-ditto
```

# Working locally with the chart

In order to test / develop the chart locally, this section should be of help.

## Testing templating
For that, no running k8s cluster is necessary - the output will be the rendered k8s deployment descriptors:

```shell
helm template my-ditto . -f values.yaml -f local-values.yaml --debug
```

## Installation
To install the Ditto chart with the name `"my-ditto"`, perform:

```shell
helm install -f values.yaml -f local-values.yaml my-ditto . --wait
kubectl port-forward svc/my-ditto-nginx 8080:8080
```

Now, you can access Ditto on [http://localhost:8080](http://localhost:8080) - have fun.

## Uninstallation
To uninstall the chart again, perform:

```shell
helm uninstall my-ditto
```
