## Eclipse Ditto :: Deployment

In order to deploy/start Ditto, you have the following options:

- [starting via Docker and docker-compose](docker/README.md)
- [starting via Kubernetes with k3s](kubernetes/k3s/README.md)
- [starting via Kubernetes with minikube](kubernetes/minikube/README.md)
- [starting via OpenShift](openshift/README.md)
- [starting via Kubernetes and Helm](helm/README.md)
- [starting via Microsoft Azure](azure/README.md)

### Resource requirements

For a "single instance" setup on a local machine (via [Docker Compose](docker/README.md) which is the most efficient 
setup for running Ditto locally) you need at least:
  * 2 CPU cores which can be used by Docker
  * 4 GB of RAM which can be used by Docker

### Operating

The [operations/](operations) folder contains helpers for operating Eclipse Ditto, like
e.g. [Grafana dashboards](operations/grafana-dashboards) to be used together with a configured
Prometheus scraping Ditto's `/metrics` HTTP endpoints.
