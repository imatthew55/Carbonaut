#!/usr/bin/env bash
# Mount the carbon data transferred from the ENACT Dataspace into GreenCharge.
set -e
cd "$(dirname "$0")/.."
kubectl create configmap carbon-feed -n enact --from-file=grid-carbon-intensity.json=data/grid-carbon-intensity.json --dry-run=client -o yaml | kubectl apply -f -
kubectl patch deploy greencharge -n enact --type=json -p='[
 {"op":"add","path":"/spec/template/spec/volumes","value":[{"name":"carbon","configMap":{"name":"carbon-feed"}}]},
 {"op":"add","path":"/spec/template/spec/containers/0/volumeMounts","value":[{"name":"carbon","mountPath":"/data"}]},
 {"op":"add","path":"/spec/template/spec/containers/0/env","value":[{"name":"CARBON_FEED_FILE","value":"/data/grid-carbon-intensity.json"},{"name":"APP_OPEN_BROWSER","value":"false"}]}]'
