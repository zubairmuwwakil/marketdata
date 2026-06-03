# SLOs & Alerts

## Suggested SLOs

- API availability: 99.9% monthly
- p95 latency: < 500ms for read endpoints
- Ingestion success rate: > 99% daily

## Example Prometheus Alerts

```yaml
groups:
  - name: marketlens
    rules:
      - alert: ApiHighErrorRate
        expr: sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
          / sum(rate(http_server_requests_seconds_count[5m])) > 0.02
        for: 10m
        labels:
          severity: page
        annotations:
          summary: "High 5xx rate"

      - alert: ApiHighLatency
        expr: histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le)) > 0.5
        for: 10m
        labels:
          severity: page
        annotations:
          summary: "High API latency"
```
