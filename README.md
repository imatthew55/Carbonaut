# Carbonaut: policy-driven, self-adapting EV charging on ENACT

Veles Hack 2026, ENACT challenge. Carbonaut extends the GreenCharge app so that it
routes EV drivers to the greenest charger **and** runs on green, in-region compute
governed by ENACT policies.

## What we built

| Area | What | Where |
|---|---|---|
| Application Policy Model | x86_64, 1–4 cores, 2Gi DDR4, max 100W, green mix ≥ 0.6, region `eu-west` | `greencharge/src/main/resources/reconciliation/policymodel.yml` |
| Application Controller | Service that loads the policy (`PolicyModelConfig`) and evaluates node metrics with `ComplianceAndAdaptationService` | `greencharge/src/main/java/eu/enact/greencharge/NodeComplianceService.java` |
| Compliance API | `GET /compliance/check` and `POST /compliance` return an `AdaptationRecommendation` | `greencharge/src/main/java/eu/enact/greencharge/NodeComplianceController.java` |
| Tests | Compliant node gives `no_action`; under-provisioned node (0.5 cores) gives `scale_up` | `greencharge/src/test/java/eu/enact/greencharge/NodeComplianceServiceTest.java` |
| Helm chart | Startup probe + TCP liveness/readiness (the app takes ~45s to boot and `/chargers` needs auth) | `greencharge/chart/templates/deployment.yaml` |
| RuntimePolicy | Soft green ≥ 0.6, Hard region `eu-west`, availability 0.9 | `greencharge/greencharge-policy.yaml` |

## Run it

```bash
make setup                                   # ENACT Kind cluster
cd greencharge && mvn clean test             # 2 tests pass
docker build -t greencharge:1.0 .
kind load docker-image greencharge:1.0 --name enact-dev
helm upgrade --install greencharge ./chart -n enact \
  --set image.repository=greencharge --set image.tag=1.0 \
  --set image.pullPolicy=IfNotPresent --set imagePullSecrets=null \
  --set podLabels.name=greencharge \
  --set resources.limits.memory=1Gi --set resources.limits.cpu=2
kubectl apply -f greencharge-policy.yaml
kubectl port-forward -n enact svc/greencharge 8080:8080
```

Login: user `user`, password from
`kubectl logs -n enact deploy/greencharge | grep "generated security password"`.

## Demo

- `http://localhost:8080/compliance/check` shows a compliant node, `no_action`
- `http://localhost:8080/compliance/check?cores=0.5` shows non-compliant, CPU `scale_up` to 1 core
- Grafana (`kubectl port-forward -n enact svc/infra-grafana 3000:80`): pod CPU/memory, plus Kepler power per pod:
  `sum by (pod_name) (rate(kepler_container_joules_total{container_namespace="enact", pod_name=~"greencharge.*"}[1m]))`

## Platform findings

- The APPLPM operator shipped with empty `METRICS_API_URL` / `CLUSTER_NAME`, so it ran with
  `NoopMetrics` and could not evaluate policies. We set them to the TDCME monitor API:
```bash
  kubectl patch deploy applpm-controller-manager -n enact --type=json -p='[{"op":"replace","path":"/spec/template/spec/containers/0/env","value":[{"name":"METRICS_API_URL","value":"http://monitor-api-service.enact.svc.cluster.local:80"},{"name":"CLUSTER_NAME","value":"dev"}]}]'
```
- The operator now reaches the monitor API but gets **401**: the monitor API requires a Bearer
  token, and the operator binary exposes no setting for one. Automatic node pinning is therefore
  pending; the pod currently runs on `enact-dev-worker` (eu-west, green ratio 0.85), which satisfies the policy.

## Status

- [x] Policy specified
- [x] AC extension developed
- [x] Tests pass
- [x] Deployed to the cluster with RuntimePolicy applied (auto-placement blocked by operator auth)
- [ ] Dataspace: consumer connector configured in the ENACT SDK (API V3 detected); awaiting API key to
      negotiate and transfer the carbon-intensity asset. The app currently uses a sample utility file.
