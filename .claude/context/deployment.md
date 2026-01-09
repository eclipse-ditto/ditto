# Deployment Options

Eclipse Ditto supports multiple deployment options, ranging from local development setups to production-ready Kubernetes deployments.

## Deployment Overview

The `deployment/` directory contains several deployment options:

### Primary Deployment Methods

1. **Helm (Kubernetes)** - ⭐ **Preferred for production** - `deployment/helm/`
2. **Docker Compose** - Best for local development - `deployment/docker/`
3. **Kubernetes (plain YAML)** - Alternative to Helm - `deployment/kubernetes/`
4. **OpenShift** - For Red Hat OpenShift - `deployment/openshift/`
5. **Azure** - Microsoft Azure specific setup - `deployment/azure/`

## Helm Deployment (Production)

**Location**: `deployment/helm/ditto/`

The official Ditto Helm chart is the **most actively maintained** deployment option and recommended for production.

### Installation

The Helm chart is published as an OCI artifact to Docker Hub:

```bash
# Create namespace
kubectl create namespace ditto

# Install Ditto
helm install -n ditto my-ditto \
  oci://registry-1.docker.io/eclipse/ditto \
  --version <version> \
  --wait
```

### Uninstallation

```bash
helm uninstall -n ditto my-ditto
```

### Key Configuration

All configuration is in `deployment/helm/ditto/values.yaml`. Key sections:

**Scaling**:
```yaml
# Scale horizontally (more instances)
things:
  replicaCount: 3

# Scale vertically (more resources per instance)
things:
  resources:
    limits:
      cpu: 1000m
      memory: 2048Mi
    requests:
      cpu: 500m
      memory: 1024Mi
```

**Environment Variables**:
```yaml
things:
  extraEnv:
    - name: LOG_INCOMING_MESSAGES
      value: "false"
```

**Java System Properties** (for HOCON config):
```yaml
things:
  systemProps:
    - "-Dpekko-contrib-mongodb-persistence-things-journal.overrides.journal-collection=my_collection"
```

### Important Files

- `values.yaml` - Main configuration file with all options
- `local-values.yaml` - Example overrides for local development
- `Chart.yaml` - Chart metadata and dependencies
- `templates/` - Kubernetes resource templates
- `service-config/` - Service-specific configuration overrides
- `nginx-config/` - Nginx reverse proxy configuration

### Resource Requirements

Default per service:
- CPU: 0.5 cores (request)
- Memory: 1024 MiB (request)

Adjust based on load and requirements.

### Advanced Configuration

For configuration options not exposed in `values.yaml`, consult service config files:
- `policies/service/src/main/resources/policies.conf`
- `things/service/src/main/resources/things.conf`
- `thingsearch/service/src/main/resources/search.conf`
- `connectivity/service/src/main/resources/connectivity.conf`
- `gateway/service/src/main/resources/gateway.conf`

Use either environment variables or Java system properties (see above examples).

## Docker Compose (Local Development)

**Location**: `deployment/docker/`

Best for **local development** - most efficient for running Ditto on a single machine.

### Resource Requirements

Minimum requirements:
- **2 CPU cores** available to Docker
- **4 GB RAM** available to Docker

### Quick Start

```bash
cd deployment/docker/

# Start with Docker Hub images
docker-compose up -d

# View logs
docker-compose logs -f

# Check resource usage
docker stats

# Stop
docker-compose down
```

### Using Local Snapshot Images

After building local Docker images:

```bash
cd deployment/docker/

# Copy dev environment config
cp dev.env .env

# Start with local images
docker-compose up -d
```

### Configuration

**Service Configuration**:
Configure via environment variables in `docker-compose.yml`:

```yaml
gateway:
  environment:
    - JAVA_TOOL_OPTIONS=-Dditto.gateway.authentication.devops.password=foobar
```

**Access Configuration**:
Query current configuration of running services:

```bash
# Get gateway configuration
curl http://devops:foobar@localhost:8080/devops/config/gateway/?path=ditto
```

**Nginx Authentication**:
- Configuration: `nginx.conf`
- Users: `nginx.htpasswd`

Add new user:
```bash
# Generate password hash
openssl passwd -quiet

# Add to nginx.htpasswd
echo "username:HASH" >> nginx.htpasswd
```

### Important Files

- `docker-compose.yml` - Service definitions and configuration
- `dev.env` - Development environment variables (SNAPSHOT version)
- `nginx.conf` - Nginx reverse proxy configuration
- `nginx.htpasswd` - Basic auth user credentials
- `nginx-cors.conf` - CORS configuration

## Kubernetes (Plain YAML)

**Location**: `deployment/kubernetes/`

Alternative to Helm for those who prefer plain Kubernetes manifests.

### Options

**k3s** (`deployment/kubernetes/k3s/`):
- Lightweight Kubernetes for local/edge deployments
- See `k3s/README.md` for instructions

**minikube** (`deployment/kubernetes/minikube/`):
- Local Kubernetes cluster for testing
- See `minikube/README.md` for instructions

**Plain manifests** (`deployment/kubernetes/deploymentFiles/`):
- Raw Kubernetes YAML files
- Can be customized with `kubectl` or `kustomize`

## OpenShift

**Location**: `deployment/openshift/`

For deploying to Red Hat OpenShift clusters.

Consult `openshift/README.md` for specific setup instructions.

## Azure

**Location**: `deployment/azure/`

Microsoft Azure-specific deployment configuration.

Consult `azure/README.md` for Azure-specific setup.

## Operations & Monitoring

**Location**: `deployment/operations/`

Contains operational tools for monitoring and managing Ditto.

### Grafana Dashboards

Pre-built Grafana dashboards for monitoring Ditto: `operations/grafana-dashboards/`

Dashboards cover:
- Service health and performance
- Message throughput
- Database operations
- JVM metrics
- Business metrics (things count, policy count, etc.)

### Prometheus

Prometheus configuration for scraping Ditto metrics: `operations/prometheus/`

All Ditto services expose metrics at `/metrics` endpoint (Prometheus format).

### Usage

1. Deploy Prometheus to scrape Ditto services
2. Configure Grafana to use Prometheus as data source
3. Import dashboards from `operations/grafana-dashboards/`

## Choosing a Deployment Option

### For Local Development
✅ **Docker Compose** (`deployment/docker/`)
- Fastest startup
- Lowest resource usage
- Easy to debug
- Quick iteration cycle

### For Production
✅ **Helm** (`deployment/helm/`)
- Most actively maintained
- Easy scaling (horizontal and vertical)
- Kubernetes-native
- Rolling updates
- Integration with K8s ecosystem

### For Testing/CI
- **Docker Compose** for integration tests
- **minikube** or **k3s** for K8s-specific testing

### For Specific Platforms
- **OpenShift** if using Red Hat OpenShift
- **Azure** if deploying to Azure with specific requirements

## Deployment Configuration Hierarchy

Configuration can be specified at multiple levels (from lowest to highest precedence):

1. **Default configuration** (in service `*.conf` files)
2. **Environment variables** (in HOCON: `${?ENV_VAR}`)
3. **Java system properties** (`-Dkey=value`)
4. **Kubernetes ConfigMaps** (mounted as files or env vars)
5. **Helm values** (passed via `--set` or values file)

## Common Deployment Tasks

### Update Ditto Version

**Docker Compose**:
```bash
export DITTO_VERSION=3.5.0
docker-compose up -d
```

**Helm**:
```bash
helm upgrade -n ditto my-ditto \
  oci://registry-1.docker.io/eclipse/ditto \
  --version 3.5.0
```

### Scale Services

**Helm**:
```bash
# Scale things service to 3 replicas
helm upgrade -n ditto my-ditto . \
  --set things.replicaCount=3 \
  --reuse-values
```

**Kubernetes**:
```bash
kubectl scale deployment ditto-things -n ditto --replicas=3
```

### Update Configuration

**Docker Compose**:
1. Edit `docker-compose.yml` environment variables
2. Run `docker-compose up -d` (recreates changed services)

**Helm**:
1. Update `values.yaml` or create override file
2. Run `helm upgrade -n ditto my-ditto . --reuse-values -f my-overrides.yaml`

### View Logs

**Docker Compose**:
```bash
docker-compose logs -f gateway
docker-compose logs -f  # all services
```

**Kubernetes/Helm**:
```bash
kubectl logs -n ditto deployment/ditto-gateway -f
kubectl logs -n ditto -l app.kubernetes.io/instance=my-ditto -f
```

### Troubleshoot Issues

**Check service health**:
- Docker Compose: `docker-compose ps`
- Kubernetes: `kubectl get pods -n ditto`

**Check resource usage**:
- Docker Compose: `docker stats`
- Kubernetes: `kubectl top pods -n ditto`

**Access service configuration**:
```bash
# DevOps endpoint (requires authentication)
curl http://devops:password@localhost:8080/devops/config/gateway/
```

## Security Considerations

### Default Credentials

⚠️ **Change default credentials in production!**

Default devops credentials:
- Username: `devops`
- Password: `foobar` (Docker Compose), check Helm values for K8s

### TLS/HTTPS

- Docker Compose: Nginx config supports TLS - add certificates to `nginx.conf`
- Helm: Use Kubernetes Ingress with TLS termination

### Network Policies

For production deployments, consider:
- Kubernetes NetworkPolicies to restrict traffic
- Service mesh for mTLS between services
- Ingress controller with authentication

## Additional Resources

- **Helm Chart Source**: `deployment/helm/ditto/`
- **Docker Compose Source**: `deployment/docker/`
- **Configuration Documentation**: https://www.eclipse.dev/ditto/installation-operating.html
- **Grafana Dashboards**: `deployment/operations/grafana-dashboards/`
- **Service Configs**: `{service}/service/src/main/resources/{service}.conf`
