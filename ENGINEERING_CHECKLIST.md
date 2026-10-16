# Engineering checklist for Docxels (from the 111-item backend / system-design list)

Docxels is an offline Android app. Its only network use is the in-app update check and download
(HTTPS only, nothing else). So most server items do not apply. This file says which do, what the app does,
and what is still open. Status words: DONE (seen in the code), OPEN (a real gap), N/A (no server, no network, no users' data leaves the phone).

## Applies, and what we do
| Item | Status | Notes |
|---|---|---|
| Encryption at rest | DONE | Scans, Word files, PDFs, signatures, letterheads and the search index are encrypted (SecureFiles). allowBackup is off. |
| Encryption in transit, TLS | DONE | Cleartext traffic is off; only system-trusted certificates (network_security_config). |
| Secrets management | OPEN | The release signing key is committed in the repo. Move to private CI secrets before Google Play (prepared, needs your upload key). No API keys exist in the app. |
| Integrity of updates | DONE | Update file and patches are checked against a SHA-256 fingerprint; the signing certificate must match the installed app. |
| Timeouts | DONE | Update requests: 15 s connect, 30 s read. |
| Retries, backoff, idempotency | OPEN | The update check does not retry with backoff; it fails and the person taps again. Re-running a download is safe (the file is verified). |
| CI/CD | DONE | Every push builds, runs unit tests, builds the release and publishes it. Failing tests are reported on the commit. |
| Build caching | DONE | Gradle cache in CI. |
| Rollbacks | PARTIAL | Each build replaces `latest`; older builds are not kept. Keeping the previous APK would give a one-step rollback. |
| Feature flags | PARTIAL | Settings switches exist (for example search inside documents is off by default); no remote flags, by design. |
| Semantic versioning, API versioning | PARTIAL | Build numbers only. update.json is the only "API"; it should carry a format version before it changes. |
| Database migrations / schema versioning | PARTIAL | Room is used for scans; later stores use plain encrypted files with their own formats. A format-version line for each store is not there yet. |
| Memory leaks, thread safety, race conditions, deadlocks | OPEN | Not audited. Stores use locks, but a review and a stress test are not done. |
| Garbage collection / memory use | OPEN | Peak memory on low-RAM phones is not measured. |
| Backpressure | OPEN | Not checked. |
| Observability: logging, metrics, alerts, tracing | OPEN | No crash reporting or telemetry, on purpose (privacy). Add an opt-in "copy diagnostics" button instead. |
| Health checks | PARTIAL | The emulator check opens the app and screenshots it; it can fail without anyone noticing if nobody looks. |
| Disaster recovery, backups | OPEN | allowBackup is off and there is no export of the whole library. A "back up everything to a file" option is the answer. |
| Cold starts, latency, throughput, tail latency | OPEN | Not measured. Add a startup-time check to the emulator run. |
| Injection, XSS, CSRF, SSRF, CORS | N/A / DONE | No web views and no server. Links are checked before opening (only http, https, mailto, tel). Files from other apps are copied in, not trusted by name. |
| Incident postmortems | OPEN | Not kept. Start a short POSTMORTEMS.md: the build 129 PDF-export problem and the failed GitHub billing step are the first two. |
| Cost optimisation | OPEN | CI cost matters now (the emulator check on every push used most of the free minutes). Run it only when asked. |
| Infrastructure as code | PARTIAL | Workflows are in the repo; the site repo and release setup are by hand. |

## Does not apply (no server, no accounts, no shared state)
Rate limiting, caching layers, load balancing, reverse proxies, API gateways, Docker, Kubernetes, service discovery,
circuit breakers, message queues, pub/sub, event-driven design, distributed transactions, sagas, dead letter queues,
cron jobs (on a server), WebSockets, long polling, server-sent events, indexing and query tuning of a server database,
connection pooling, read replicas, sharding, partitioning, replication, leader election, CAP, eventual consistency,
locking across machines, autoscaling, horizontal / vertical scaling, CDN, edge caching, cache invalidation,
blue-green and canary releases, rolling deployments, liveness probes, IAM, OAuth, JWT, WAF, DDoS protection,
multi-region, failover, chaos engineering, network partitions, clock skew, DNS, TCP vs UDP, HTTP/2 and 3, gRPC,
webhooks, Terraform, Helm, on-call.

If a feature ever adds a server (cloud sync, an AI assistant, billing), this list is the starting point for it,
and each of those items must be answered before it ships.
