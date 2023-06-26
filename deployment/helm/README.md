# Eclipse Ditto :: Helm

The official Ditto Helm chart is managed here, in folder [ditto](ditto/).  
It is deployed as "OCI artifact" to Docker Hub at: https://hub.docker.com/r/eclipse/ditto

## Install Ditto via Helm Chart

To install the chart with the release name eclipse-ditto, run the following commands:

```shell script
helm install -n ditto my-ditto oci://registry-1.docker.io/eclipse/ditto --version <version> --wait
```

# Uninstall the Helm Chart

To uninstall/delete the `my-ditto` deployment:

```shell script
helm uninstall my-ditto
```
