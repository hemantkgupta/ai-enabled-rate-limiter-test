# Performance Validation

## Requirements
- Handle 10,000 requests/second.
- Latency < 1ms per check.
- Support 1,000 concurrent clients.

## Test
`src/test/java/com/meta/ratelimiter/PerformanceTest.java`

Behavior:
- Spawns 1,000 concurrent clients.
- Sends 10,000 total requests.
- Measures throughput and P95/P99 latency.

Metrics:
- **Throughput (req/s)**: totalRequests / elapsedSeconds.
- **P95/P99 latency (ms)**: percentile of per-request durations.

Indicators of Performance Problems
- Throughput < 10k req/s.
- P95 latency >= 1ms.
- Timeouts or incomplete requests.
