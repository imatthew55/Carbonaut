# Carbonaut

**Veles Hack 2026, ENACT challenge.** Carbonaut is our ENACT-enhanced version of **GreenCharge**, the challenge's Spring Boot app that routes EV drivers to the greenest available charger. We made the app policy-aware: it states its own requirements, checks whether a node meets them, and is packaged and deployed with the ENACT SDK onto the green node.

## What we built

| Part | What | Where |
|---|---|---|
| Application Policy Model | x86_64, 1–4 cores, 2Gi DDR4, latency ≤ 50 ms, max 100 W, green mix ≥ 0.6, region `eu-west` | `greencharge/src/main/resources/reconciliation/policymodel.yml` |
| Application Controller | Loads the policy model (`PolicyModelConfig`) and evaluates node metrics with `ComplianceAndAdaptationService` | `greencharge/src/main/java/eu/enact/greencharge/NodeComplianceService.java` |
| Compliance API | `GET /compliance/check?cores=&memGi=&latencyMs=` and `POST /compliance` return an `AdaptationRecommendation` | `greencharge/src/main/java/eu/enact/greencharge/NodeComplianceController.java` |
| Tests | 2 cores → compliant, `no_action`; 0.5 cores → non-compliant, `scale_up` | `greencharge/src/test/java/eu/enact/greencharge/NodeComplianceServiceTest.java` |
| Helm chart (ENACT SDK, Application Packaging) | Generated chart, adapted: TCP + startup probes, pod label `name: greencharge`, node selector `enact.eu/role: edge` | `greencharge/enact-chart/` |
| RuntimePolicy (ENACT SDK, Application Policies) | Soft green ≥ 0.6, Hard region `eu-west`, node availability 0.9, CPU 1–4, memory ≥ 2048 MB, role `edge`; validated against the ENACT CRD | `greencharge/.enact/policies/greencharge-policy.yaml` |

| Dataspace (ENACT SDK, Dataspaces) | `grid-carbon-intensity` asset negotiated and transferred from the ENACT provider (`enact-dataspace.iti.gr`); mounted into the pod as ConfigMap `carbon-feed`, read via `CARBON_FEED_FILE` | `greencharge/data/grid-carbon-intensity.json`, `greencharge/k8s/carbon-feed.sh` |

Deployed with the ENACT SDK **Application Deployment** module to the local Kind cluster: RuntimePolicy, Service, Deployment and Ingress applied. The policy operator selects **`enact-dev-worker`** (eu-west, green ratio 0.85) and the pod runs there.

## Run it

Cluster setup is in [SETUP.md](SETUP.md) (`make setup`). Then:

```bash
cd greencharge
mvn clean test                                   # 2 tests pass
docker build -t greencharge:1.0 .
kind load docker-image greencharge:1.0 --name enact-dev
kubectl apply -f .enact/policies/greencharge-policy.yaml
kubectl apply -n enact -f enact-chart/manifests/greencharge.yaml
bash k8s/carbon-feed.sh                            # live dataspace carbon data
kubectl port-forward -n enact svc/greencharge 8080:8080
```

Log in as `user` with the password from `kubectl logs -n enact deploy/greencharge | grep "generated security password"`, then open:

- `http://localhost:8080/compliance/check`: compliant, `no_action`
- `http://localhost:8080/compliance/check?cores=0.5`: non-compliant, `scale_up` to 1 core
- `http://localhost:8080/carbon`: `"source":"live (dataspace file)"`. With the live data Harbor (55 gCO₂/kWh) is greenest, so the recommendation moves from Riverside Central (mock) to Harbor Docks.

Energy per pod (Kepler) in Grafana or Prometheus:
`sum by (pod_name) (rate(kepler_container_joules_total{container_namespace="enact", pod_name=~"greencharge.*"}[1m]))`

## Findings

- **Health probes:** the generated chart probes `/health`, which GreenCharge doesn't expose, and the app needs about 45 s to start, so it crash-looped. Fixed with TCP probes and a startup probe.
- **Policy operator:** APPLPM ships with empty `METRICS_API_URL` and `CLUSTER_NAME`, so it ran with `NoopMetrics` and could not evaluate policies. We pointed it at the TDCME monitor API and, following the mentor's tip, passed TDCME's admin token as `METRICS_API_TOKEN`:
  ```bash
  kubectl patch deploy applpm-controller-manager -n enact --type=json -p='[{"op":"replace","path":"/spec/template/spec/containers/0/env","value":[{"name":"METRICS_API_URL","value":"http://monitor-api-service.enact.svc.cluster.local:80"},{"name":"CLUSTER_NAME","value":"dev"},{"name":"METRICS_API_TOKEN","valueFrom":{"secretKeyRef":{"name":"admin-token-secret","key":"token"}}}]}]'
  ```
  The operator now evaluates the RuntimePolicy on live TDCME metrics: status `DecisionReady`, `chosenNode: enact-dev-worker` (availability 1, green ratio 0.85, eu-west).

## Status

- [x] Policy model, Application Controller extension, tests
- [x] Packaged, policy-validated and deployed with the ENACT SDK; pod on `enact-dev-worker`
- [x] Monitored in Grafana and Kepler under load
- [x] **Dataspace:** `grid-carbon-intensity` transferred from the ENACT Data & Object Space with the SDK; the running app uses it (`live (dataspace file)`)
- [x] Policy operator evaluates the RuntimePolicy on TDCME metrics: `DecisionReady`, chosen node `enact-dev-worker`

## License

Apache License 2.0, see [LICENSE](LICENSE).
