# Eclipse Ditto

## Introduction

[Eclipse Ditto™](https://www.eclipse.org/ditto/) is a technology in the IoT implementing a software pattern called “digital twins”.
A digital twin is a virtual, cloud based, representation of his real world counterpart (real world “Things”, e.g. devices like sensors, smart heating, connected cars, smart grids, EV charging stations, …).

This chart uses `eclipse/ditto-XXX` containers to run Ditto inside Kubernetes.

## Prerequisites

* Has been tested on Kubernetes 1.11+

## Installing the Chart

To install the chart with the release name `eclipse-ditto`, run the following command:

```bash
helm install kiwigrid/eclipse-ditto --name eclipse-ditto
```

## Uninstalling the Chart

To uninstall/delete the `eclipse-ditto` deployment:

```bash
helm delete eclipse-ditto
```

The command removes all the Kubernetes components associated with the chart and deletes the release.

> **Tip**: To completely remove the release, run `helm delete --purge eclipse-ditto`

## Configuration

Please view the `values.yaml` for the list of possible configuration values with its documentation.

Specify each parameter using the `--set key=value[,key=value]` argument to `helm install`. For example:

```bash
helm install --name eclipse-ditto --set swaggerui.enabled=false kiwigrid/eclipse-ditto
```

Alternatively, a YAML file that specifies the values for the parameters can be provided while installing the chart.

## Configuration Examples

### OpenID Connect (OIDC)

To enable OIDC authentiaction adjust following properties:

```yaml
global:
  jwtOnly: true

gateway:
  enableDummyAuth: false
  systemProps:
    - "-Dditto.gateway.authentication.oauth.openid-connect-issuers.myprovider=openid-connect.onelogin.com/oidc"
```

### Securing Devops Resource

To secure /devops and /status resource adjust configuration to (username will be `devops`):

```yaml
gateway:
  enableDummyAuth: false
  devopsSecureStatus: true
  devopsPassword: foo
  statusPassword: bar
``
