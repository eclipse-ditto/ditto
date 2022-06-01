# Eclipse Ditto :: Helm

The Ditto Helm chart is managed at the [Eclipse IoT Packages](https://github.com/eclipse/packages/tree/master/charts/ditto) project.

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
