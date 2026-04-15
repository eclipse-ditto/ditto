# Eclipse Ditto

## Introduction

[Eclipse Ditto™](https://www.eclipse.dev/ditto/) is a technology in the IoT implementing a software pattern
called “digital twins”. A digital twin is a virtual, cloud based, representation of his real world counterpart
(real world “Things”, e.g. devices like sensors, smart heating, connected cars, smart grids, EV charging stations, …).

This chart uses `eclipse/ditto-XXX` containers to run Ditto inside Kubernetes.

## Prerequisites

TL;DR:

* have a correctly configured [`kubectl`](https://kubernetes.io/docs/tasks/tools/#kubectl) (either against a local or remote k8s cluster)
* have [Helm installed](https://helm.sh/docs/intro/)

The Helm chart is being tested to successfully install on the most recent Kubernetes versions.

## Installing the Chart

The instructions below illustrate how Ditto can be installed to the `ditto` namespace in a Kubernetes cluster using
release name `eclipse-ditto`.  
The commands can easily be adapted to use a different namespace or release name.

The target namespace in Kubernetes only needs to be created if it doesn't exist yet:

```bash
kubectl create namespace ditto
```

The chart can then be installed to namespace `ditto` using release name `my-ditto`:

```bash
helm install --dependency-update -n ditto my-ditto oci://registry-1.docker.io/eclipse/ditto --version <version> --wait
```


## Uninstalling the Chart

To uninstall/delete the `my-ditto` release:

```bash
helm uninstall -n ditto my-ditto
```

The command removes all the Kubernetes components associated with the chart and deletes the release.

## Configuration

Please view the `values.yaml` for the list of possible configuration values with its documentation.

Specify each parameter using the `--set key=value[,key=value]` argument to `helm install`. For example:

```bash
helm install -n ditto my-ditto oci://registry-1.docker.io/eclipse/ditto --version <version> --set swaggerui.enabled=false
```

Alternatively, a YAML file that specifies the values for the parameters can be provided while installing the chart.

## Chart configuration options

Please consult the [values.yaml](https://github.com/eclipse-ditto/ditto/blob/master/deployment/helm/ditto/values.yaml) 
for all available configuration options of the Ditto Helm chart.  

### Scaling options

Please note the defaults the chart comes with:
* the default deploy 1 instance per Ditto service
* each Ditto service is configured to require:
  * 0.5 CPUs
  * 1024 MiB of memory

Adjust this to your requirements, e.g. scale horizontally by configuring a higher `replicaCount` or vertically by 
configuring more resources.

## Advanced configuration options

Even more configuration options, not exposed to the `values.yaml`, can be configured using either environment variables
or Java "System properties".  
To inspect all available configuration options, please inspect Ditto's service configurations:

* [policies.conf](https://github.com/eclipse-ditto/ditto/blob/master/policies/service/src/main/resources/policies.conf)
* [things.conf](https://github.com/eclipse-ditto/ditto/blob/master/things/service/src/main/resources/things.conf)
* [things-search.conf](https://github.com/eclipse-ditto/ditto/blob/master/thingsearch/service/src/main/resources/search.conf)
* [connectivity.conf](https://github.com/eclipse-ditto/ditto/blob/master/connectivity/service/src/main/resources/connectivity.conf)
* [gateway.conf](https://github.com/eclipse-ditto/ditto/blob/master/gateway/service/src/main/resources/gateway.conf)


### Configuration via environment variables

In order to provide an environment variable config overwrite, simply put the environment variable in the `extraEnv` 
of the Ditto service you want to specify the configuration for.

E.g. if you want to configure the `LOG_INCOMING_MESSAGES` for the `things` service to be disabled:  
```yaml
things:
  # ...
  extraEnv:
    - name: LOG_INCOMING_MESSAGES
      value: "false"
```

### Configuration via system properties

Not all Ditto/Pekko configuration options have an environment variable overwrite defined in the configuration.  
For configurations without such an environment variable overwrite, the option can be configured via Java system property.  
The documentation on how this works can be found in the 
[HOCON documentation](https://github.com/lightbend/config/blob/main/HOCON.md#conventional-override-by-system-properties),
which is the configuration format Ditto uses.

E.g. if you want to adjust the `journal-collection` name which the `things` service uses to write its
journal entries to MongoDB (which can be found [here](https://github.com/eclipse-ditto/ditto/blob/33a38bc04b47d0167ba0e99fe76d96a54aa3d162/things/service/src/main/resources/things.conf#L268)),
simply configure:


```yaml
things:
  # ...
  systemProps:
    - "-Dpekko-contrib-mongodb-persistence-things-journal.overrides.journal-collection=another_fancy_name"
```


## Troubleshooting

If you experience high resource consumption (either CPU or RAM or both), you can limit the resource usage by
specifying resource limits.
This can be done individually for each single component.
Here is an example how to limit CPU to 0.25 Cores and RAM to 512 MiB for the `connectivity` service:

```bash
helm upgrade -n ditto eclipse-ditto . --install --set connectivity.resources.limits.cpu=0.25 --set connectivity.resources.limits.memory=512Mi
```
