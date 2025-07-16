#!/bin/sh
# Copyright (c) 2024 Contributors to the Eclipse Foundation
#
# See the NOTICE file(s) distributed with this work for additional
# information regarding copyright ownership.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0
#
# SPDX-License-Identifier: EPL-2.0

SERVICEACCOUNT=/var/run/secrets/kubernetes.io/serviceaccount
TOKEN=$(cat ${SERVICEACCOUNT}/token)
CACERT=${SERVICEACCOUNT}/ca.crt
NAMESPACE=$(cat ${SERVICEACCOUNT}/namespace)
WORKING_FOLDER="/tmp/pod-deletion-script-$(date +%Y-%m-%d-%H-%M-%S)"

mkdir -p $WORKING_FOLDER
cd $WORKING_FOLDER

echo "Retrieving current pods, ips and their deletion cost in working dir: $WORKING_FOLDER ..."
# access k8s pods information and extract "pod name", "internal pod IP" and "pod deletion cost" into file `pod_ip_cost.json`:
curl --fail --silent --cacert ${CACERT} -H "Authorization: Bearer ${TOKEN}" \
  "https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT/api/v1/namespaces/${NAMESPACE}/pods" \
  | jq '.items | map(select(.metadata.labels.actorSystemName == "ditto-cluster") | { pod: .metadata.name, ip: .status.podIP, cost: .metadata.annotations."controller.kubernetes.io/pod-deletion-cost"})' \
  > pod_ip_cost.json
curlExitCode=$?
if [ $curlExitCode -ne 0 ]; then
  echo "Retrieving current pods curl failed [exit-code: $curlExitCode]"
  exit 1
fi

# extract the first internal IP from `pod_ip_cost.json` in order to lookup Apache Pekko cluster membership data:
somePekkoClusterIp=$(jq -r '.[0].ip' pod_ip_cost.json)
echo "Accessing current Pekko Cluster members from internal ip: $somePekkoClusterIp ..."
curl --fail --silent -o pekko_cluster_members.json http://$somePekkoClusterIp:7626/cluster/members
curlExitCode=$?
if [ $curlExitCode -ne 0 ]; then
  echo "Accessing current Pekko Cluster members curl failed [exit-code: $curlExitCode]"
  exit 1
fi

echo "Finding out all oldest ..."
# find out all "oldest" pods (per role and the "overall oldest" - specifying the "cost" for deletion in here as well):
jq '.oldestPerRole | to_entries | map(.value | split("@") | last | split(":") | first | { ip: . }) | group_by(.ip) | map(.[]+{"cost":length}) | unique_by(.ip) | .[].cost *= 100' pekko_cluster_members.json \
  > ip_to_new_cost.json

echo "Merging pods and their internal ip addresses with pod deletion cost ..."
# merge pods and their internal IP addresses together with the calculated pod deletion cost:
jq 'INDEX(.ip)' ip_to_new_cost.json > ip_to_new_cost_by_ip.json
jq 'map(del(.cost)) | INDEX(.ip)' pod_ip_cost.json > pods_by_ip.json
jq -s '.[0] * .[1] | to_entries | map(select(.value.cost != null).value)' pods_by_ip.json ip_to_new_cost_by_ip.json \
  > new_cost_pod_and_ip.json

# clear remaining ones - which had a cost from "last run", but now don't any longer
jq -r '.[] | select(.cost != null) | .pod' pod_ip_cost.json > pods_with_old_cost.txt
while read pod; do
  grep -R $pod new_cost_pod_and_ip.json
  if [ $? -eq 0 ]; then
     #pod is in file at least once
     echo "Not clearing pod-deletion-cost of pod: $pod"
  else
     #pod is not in file
     echo "Clearing pod-deletion-cost of pod: $pod"
     curl -X PATCH --silent --output /dev/null --show-error --fail --cacert ${CACERT} -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/merge-patch+json' \
        "https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT/api/v1/namespaces/${NAMESPACE}/pods/${pod}" \
        --data '{"metadata": {"annotations": {"controller.kubernetes.io/pod-deletion-cost": null }}}'
     curlExitCode=$?
     if [ $curlExitCode -ne 0 ]; then
       echo "Clearing pod-deletion-cost curl failed [exit-code: $curlExitCode]"
       exit 1
     fi
  fi
done <pods_with_old_cost.txt

echo "Starting to patch pods with updated pod deletion costs ..."
jq -r '.[] | [.pod, .ip, .cost] | @tsv' new_cost_pod_and_ip.json |
  while IFS=$(printf '\t') read -r pod ip cost; do
    echo "Patching pod-deletion-cost of pod: $pod to: $cost"
    curl -X PATCH --silent --output /dev/null --show-error --fail --cacert ${CACERT} -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/merge-patch+json' \
       "https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT/api/v1/namespaces/${NAMESPACE}/pods/${pod}" \
       --data '{"metadata": {"annotations": {"controller.kubernetes.io/pod-deletion-cost": '\""$cost"\"' }}}'
    curlExitCode=$?
    if [ $curlExitCode -ne 0 ]; then
      echo "Patching pod-deletion-cost curl failed [exit-code: $curlExitCode]"
      exit 1
    fi
  done

echo "Pod deletion costs after script finished:"
curl --fail --cacert ${CACERT} -H "Authorization: Bearer ${TOKEN}" \
  "https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT/api/v1/namespaces/${NAMESPACE}/pods" \
  | jq '.items | map(select(.metadata.labels.actorSystemName == "ditto-cluster") | { pod: .metadata.name, ip: .status.podIP, cost: .metadata.annotations."controller.kubernetes.io/pod-deletion-cost"})'

echo "DONE"
