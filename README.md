# DispatchIQ — Adaptive Batch Ride Dispatch Engine

> A high-throughput, resource-constrained ride dispatch system built in Java.  
> Demonstrates how to handle 500+ concurrent clients with a fixed pool of 5 workers — without dropping work, starving clients, or blowing up memory.

---

## 🎬 Demo Video
*[Link to your video here]*

---

## The Problem

Ride-hailing systems face a fundamental resource mismatch: thousands of riders submitting requests per minute, but a limited number of backend workers to process them. Naive solutions either:

- **Block** — one thread per request. Collapses under load.
- **Drop silently** — unbounded queues that accept everything until OOM.
- **Match greedily** — assign the closest driver to each request one at a time. Fast, but globally suboptimal.

DispatchIQ solves all three.

---

## Architecture

```
500 concurrent riders
        │
        ▼
┌─────────────────────────────────┐
│  Bounded Ingest Queue (cap 500) │  ← Backpressure: 503 when full
└─────────────────┬───────────────┘
                  │
        ┌─────────▼──────────┐
        │  5 Dispatch Workers │  ← Fixed pool, regardless of client count
        │  (every 500ms)      │
        └─────────┬──────────┘
                  │  drainBatch(20 requests)
                  ▼
        ┌─────────────────────┐
        │   BatchMatcher      │  ← SLA-aware greedy cost minimization
        │   Algorithm         │
        └─────────┬───────────┘
                  │
        ┌─────────▼───────────┐
        │  50 Drivers         │  ← Atomic CAS assignment, no double-booking
        │  (auto-complete      │
        │   trips, re-queue)  │
        └─────────────────────┘
```

---

## Key Design Decisions

### 1. Bounded Queue + Backpressure
`ArrayBlockingQueue` with a fixed capacity of 500. When full, new requests get an immediate `503 Service Unavailable` with a `Retry-After` header — not a blocked thread, not a silent drop.

**Why:** An unbounded queue would accept everything but silently accumulate memory pressure. A bounded queue makes overload *visible* and *recoverable*.

### 2. Adaptive Batch Matching (vs. Greedy One-at-a-Time)
Every 500ms, each worker drains up to 20 requests and finds the globally optimal assignment across all of them — rather than matching request #1 to the closest driver, then request #2 to the next closest, etc.

**Why greedy one-at-a-time fails at scale:**  
Request #1 might "steal" the driver who was perfect for request #2, forcing a far worse match. Batch matching considers all (request, driver) pairs together and minimizes total cost.

### 3. SLA-Aware Cost Adjustment
Requests waiting longer than 5 seconds get their effective matching cost reduced. This means a rider who has been waiting 10 seconds will be matched even if their nearest driver is slightly farther than a new rider's nearest driver.

**Why:** Without this, a steady stream of new requests would perpetually get priority over older ones — causing starvation for requests that arrived during peak load.

### 4. Atomic Driver Assignment (CAS)
`Driver.tryAssign()` uses `AtomicReference.compareAndSet(AVAILABLE, ON_TRIP)`. If two batch workers try to assign the same driver simultaneously, only one wins. No locks, no synchronized blocks.

**Why:** Multiple workers run concurrently. Without CAS, two workers could both see a driver as AVAILABLE and double-book them.

### 5. Natural Queue Drain
Drivers automatically complete their trips after a simulated duration and return to AVAILABLE. This means under sustained load, the system self-regulates — it doesn't need manual intervention to recover.

---

## Running Locally

**Prerequisites:** Java 17+, Maven 3.8+, Python 3.x

```bash
# Build and run
mvn spring-boot:run

# In another terminal — fire 500 concurrent ride requests
python load_test.py --riders 500

# Watch live metrics
curl http://localhost:8080/api/metrics | python -m json.tool
```

---

## API Reference

| Endpoint | Method | Description |
|---|---|---|
| `/api/rides` | POST | Submit a ride request |
| `/api/rides/{id}` | GET | Poll request status |
| `/api/drivers` | GET | List all drivers + status |
| `/api/metrics` | GET | Live system metrics |

**Submit a ride:**
```json
POST /api/rides
{
  "riderId": "rider-001",
  "pickupLat": 0.45, "pickupLng": 0.32,
  "dropoffLat": 0.78, "dropoffLng": 0.91
}
```

**Response (accepted):** `202 Accepted`  
**Response (backpressure):** `503 Service Unavailable` + `Retry-After: 2`

---

## What the Metrics Show

```json
{
  "queue": {
    "currentDepth": 47,
    "capacity": 500,
    "fillPercentage": "9.4%",
    "totalEnqueued": 423,
    "totalRejected": 77        ← backpressure events
  },
  "engine": {
    "workerCount": 5,
    "totalBatchesProcessed": 84,
    "totalMatched": 310
  },
  "drivers": {
    "available": 12,
    "onTrip": 38,
    "total": 50
  },
  "matching": {
    "batchEfficiencyPct": "89.4%",   ← % of batched requests that got matched
    "averageMatchDistanceKm": "0.2341"
  }
}
```

---

## Tech Stack

- **Java 17** — records, sealed types, modern concurrency
- **Spring Boot 3.2** — REST API, Actuator, lifecycle management
- **`java.util.concurrent`** — `ArrayBlockingQueue`, `ScheduledExecutorService`, `AtomicReference`, `LongAdder`
- **No external message brokers** — the queue, workers, and matching are all hand-built to show the mechanics

---

## What's Next (Enhancements)

- **Hungarian algorithm** for true optimal batch assignment (currently approximated with greedy cost sort)
- **Zone partitioning** — city grid split into regions, each with its own queue, to reduce cross-region coordination
- **Pre-matching** — speculatively assign drivers to queued requests while they finish their current trip
- **Prometheus + Grafana** — replace the JSON metrics endpoint with a real dashboard

---

*Built by Antarang Poogalia — Backend Engineer*  
*Java | Distributed Systems | High-Throughput Infrastructure*
