---
title: "Helm chart now ingress controller agnostic"
published: true
permalink: 2026-04-01-ingress-controller-agnostic-helm-chart.html
layout: post
author: kalin_kostashki
tags: [blog, kubernetes, helm]
hide_sidebar: true
sidebar: false
toc: true
---

Starting with Helm chart version 4.0.0, Eclipse Ditto's Kubernetes deployment is no longer tied to a specific ingress
controller. The chart now provides clean, standard Kubernetes Ingress resources that work with **any** ingress controller
— whether you use ingress-nginx, Traefik, HAProxy, Kong, or any other implementation.

## What changed

Previously, the Ditto Helm chart bundled an entire **ingress-nginx controller deployment** (854 lines of RBAC,
Deployments, Services, admission webhooks, and IngressClass) and hardcoded `nginx.ingress.kubernetes.io/*` annotations
across all Ingress resource templates. This forced users to use ingress-nginx and prevented adoption of other ingress
controllers.

In version 4.0.0, we made the following changes:

- **Removed** the bundled ingress-nginx controller deployment — users bring their own ingress controller
- **Removed** all nginx-specific annotations from Ingress resources — annotations are now empty by default
- **Simplified** path types to standard `Prefix` and `Exact` — no more controller-specific regex paths
- **Added** per-group `enabled` flags — each route group (api, ws, ui, devops) can be independently toggled
- **Replaced** the `backendSuffix` pattern with explicit `service.name` references

## Breaking changes

This is a **major version bump** (`3.x` → `4.0.0`) with breaking changes to `values.yaml`:

- `ingress.controller.*` — entire block removed (no longer deploying a controller)
- `ingress.annotations` — global nginx-specific annotations removed
- `ingress.api.kubernetesAuthAnnotations` — removed
- `ingress.className` — default changed from `"nginx"` to `""` (empty)
- `ingress.host` — default changed from `"localhost"` to `""` (catch-all)
- `backendSuffix` — replaced by `service.name` in each path entry
- All per-group nginx-specific annotation blocks — removed

## New values.yaml structure

The ingress section is now clean and controller-agnostic:

```yaml
ingress:
  enabled: false
  className: ""        # Set to your controller's IngressClass
  host: ""             # Optional hostname — if empty, no host rule (catch-all)
  tls: []

  api:
    enabled: true
    annotations: {}    # Add your controller-specific annotations here
    paths:
      - path: /api
        pathType: Prefix
        service:
          name: gateway
      - path: /stats
        pathType: Prefix
        service:
          name: gateway
      - path: /overall
        pathType: Prefix
        service:
          name: gateway

  ws:
    enabled: true
    annotations: {}
    paths:
      - path: /ws
        pathType: Prefix
        service:
          name: gateway

  ui:
    enabled: true
    annotations: {}
    # defaultBackend serves as the catch-all for unmatched paths (e.g. the landing page)
    defaultBackend:
      service:
        name: nginx
    paths:
      - path: /
        pathType: Exact
        service:
          name: nginx
      - path: /apidoc
        pathType: Prefix
        service:
          name: swaggerui
      - path: /ui
        pathType: Prefix
        service:
          name: dittoui

  devops:
    enabled: true
    annotations: {}
    paths:
      - path: /devops
        pathType: Prefix
        service:
          name: gateway
      - path: /health
        pathType: Prefix
        service:
          name: gateway
      - path: /status
        pathType: Prefix
        service:
          name: gateway
      - path: /api/2/connections
        pathType: Prefix
        service:
          name: gateway
```

## Deployment modes

The chart supports three deployment modes:

### Standalone nginx pod (default, unchanged)

The standalone nginx reverse proxy remains the default for local and development setups.
No ingress controller needed — access Ditto via port-forward:

```bash
helm install ditto ditto/ditto
kubectl port-forward svc/ditto-nginx 8080:8080
```

### Ingress with nginx pod

Use Ingress resources for external access while keeping the nginx pod for the landing page and static resources:

```bash
helm install ditto ditto/ditto \
  --set ingress.enabled=true \
  --set ingress.className=nginx \
  --set ingress.host=ditto.example.com
```

### Ingress only (no nginx pod)

For production setups where the landing page is not needed, disable the nginx pod and the UI ingress's root path:

```bash
helm install ditto ditto/ditto \
  --set ingress.enabled=true \
  --set ingress.className=nginx \
  --set ingress.host=ditto.example.com \
  --set nginx.enabled=false
```

## Controller-specific configuration

Since controller-specific behavior (authentication, CORS, timeouts, URL rewriting) is now the user's responsibility,
here are examples for common ingress controllers.

### ingress-nginx

For the Ditto UI and Swagger API docs, ingress-nginx requires regex paths and a rewrite annotation to strip the
path prefix before forwarding to the backend.

Note that when `use-regex` is enabled, the root path `/` must be specified as `/$` with `pathType: ImplementationSpecific`
to ensure it matches only the exact root path and does not conflict with the ingress controller's default catch-all:

```yaml
ingress:
  enabled: true
  className: nginx
  host: ditto.example.com
  api:
    annotations:
      nginx.ingress.kubernetes.io/proxy-read-timeout: "70"
      nginx.ingress.kubernetes.io/proxy-send-timeout: "70"
  ws:
    annotations:
      nginx.ingress.kubernetes.io/proxy-read-timeout: "86400"
      nginx.ingress.kubernetes.io/proxy-send-timeout: "86400"
      nginx.ingress.kubernetes.io/proxy-buffering: "off"
  ui:
    annotations:
      nginx.ingress.kubernetes.io/use-regex: "true"
      nginx.ingress.kubernetes.io/rewrite-target: /$2
    paths:
      - path: /$
        pathType: ImplementationSpecific
        service:
          name: nginx
      - path: /apidoc(/|$)(.*)
        pathType: ImplementationSpecific
        service:
          name: swaggerui
      - path: /ui(/|$)(.*)
        pathType: ImplementationSpecific
        service:
          name: dittoui
```

### Traefik

Traefik requires a `Middleware` CRD to strip path prefixes. First, create the middleware in the Ditto namespace:

```yaml
apiVersion: traefik.io/v1alpha1
kind: Middleware
metadata:
  name: strip-prefix
  namespace: ditto
spec:
  stripPrefix:
    prefixes:
      - /ui
      - /apidoc
```

Then reference it in the ingress configuration:

```yaml
ingress:
  enabled: true
  className: traefik
  host: ditto.example.com
  ui:
    annotations:
      traefik.ingress.kubernetes.io/router.middlewares: ditto-strip-prefix@kubernetescrd
```

### HAProxy

```yaml
ingress:
  enabled: true
  className: haproxy
  host: ditto.example.com
  ui:
    annotations:
      haproxy.org/path-rewrite: "/ /"
```

## What stays the same

- The **standalone nginx pod** for local/development deployments is unchanged
- The **OpenShift Route** template is unchanged
- All **Ditto service templates** (gateway, things, policies, connectivity, etc.) are unchanged
- The **4 route groups** (api, ws, ui, devops) cover all Ditto endpoints — the root landing page is now part of the ui group

## Migrating from 3.x

1. **Install your ingress controller separately** (e.g., via its own Helm chart)
2. **Update your values.yaml** to match the new structure — replace `backendSuffix` with `service.name`,
   remove `ingress.controller.*` and global annotations
3. **Add controller-specific annotations** to the appropriate route groups
4. **Upgrade the chart** to version 4.0.0

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
