# Eclipse Ditto deployment on Microsoft Azure

This guide describes the most simplistic installation of Eclipse Ditto on Microsoft Azure. It is not meant for productive use but rather for evaluation as well as demonstration purposes or as a baseline to evolve a production grade [Application architecture](https://docs.microsoft.com/en-us/azure/architecture/guide/) out of it which includes Eclipse Ditto.

## Prerequisites

- An [Azure subscription](https://azure.microsoft.com/en-us/get-started/).
- [Azure CLI](https://docs.microsoft.com/en-us/cli/azure/install-azure-cli) installed to setup the infrastructure.
- [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/) and [helm](https://helm.sh/docs/using_helm/#installing-helm) installed to deploy Ditto into [Azure Kubernetes Service (AKS)](https://docs.microsoft.com/en-us/azure/aks/intro-kubernetes).

## HowTo install Ditto

First we are going to setup the basic Kubernetes infrastructure.

As described [here](https://docs.microsoft.com/en-gb/azure/aks/kubernetes-service-principal) we will create an explicit service principal first. You can add roles to this principal later, e.g. to access a [Azure Container Registry (ACR)](https://docs.microsoft.com/en-us/azure/container-registry/container-registry-intro).

```bash
service_principal=`az ad sp create-for-rbac --name http://dittoServicePrincipal --skip-assignment --output tsv`
export app_id_principal=`echo $service_principal|cut -f1 -d ' '`
export password_principal=`echo $service_principal|cut -f4 -d ' '`
```

Note: it might take a few seconds until the principal is available to the cluster in the later steps. So maybe time to get up and stretch a bit.

```bash
resourcegroup_name=armtest
az group create --name $resourcegroup_name --location "westeurope"
```

With the next command we will use the provided [Azure Resource Manager (ARM)](https://docs.microsoft.com/en-us/azure/azure-resource-manager/resource-group-overview) templates to setup the AKS cluster. This might take a while. So maybe time to try out this meditation thing :smile:

```bash
unique_solution_prefix=myprefix
az group deployment create --name DittoBasicInfrastructure --resource-group $resourcegroup_name --template-file arm/dittoInfrastructureDeployment.json --parameters uniqueSolutionPrefix=$unique_solution_prefix servicePrincipalClientId=$app_id_principal servicePrincipalClientSecret=$password_principal
```

The output of the command will provide you with the name of your AKS cluster which should be `YOUR_PREFIXdittoaks`.

Now you can set your cluster in `kubectl`.

```bash
aks_cluster_name=$unique_solution_prefix
aks_cluster_name+="dittoaks"
az aks get-credentials --resource-group $resourcegroup_name --name $aks_cluster_name
```

Next deploy helm on your cluster. It will take a moment until tiller is booted up. So maybe time again to get up and stretch a bit.

```bash
kubectl apply -f helm-rbac.yaml
helm init --service-account tiller
```

(Optional): in case you want to use MongoDB with persistent storage as part of your setup we will need a k8s `StorageClass` with `Retain` policy.

```bash
kubectl apply -f managed-premium-retain.yaml
```

Now install Ditto with helm

```bash
k8s_namespace=dittons

kubectl create namespace $k8s_namespace
```

...and download charts Ditto depends on:

```bash
helm dependency update ../helm/eclipse-ditto/
```

...either with persistent storage:

```bash
helm upgrade ditto ../helm/eclipse-ditto/ --namespace $k8s_namespace --set service.type=LoadBalancer,mongodb.persistence.enabled=true,mongodb.persistence.storageClass=managed-premium-retain --wait --install
```

...or without:

```bash
helm upgrade ditto ../helm/eclipse-ditto/ --namespace $k8s_namespace --set service.type=LoadBalancer --wait --install
```

Have fun with Eclipse Ditto on Microsoft Azure!
