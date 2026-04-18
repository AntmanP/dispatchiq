"""
DispatchIQ Load Test
====================
Simulates N riders simultaneously submitting ride requests.
Watch the metrics at http://localhost:8080/api/metrics while this runs.

Usage:
    python load_test.py              # default: 500 riders
    python load_test.py --riders 100 # custom count
    python load_test.py --riders 500 --delay 0  # max pressure, no delay
"""

import argparse
import random
import time
import json
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field


@dataclass
class Stats:
    accepted: int = 0
    rejected: int = 0
    errors: int = 0
    latencies: list = field(default_factory=list)


def submit_ride(rider_id: str) -> dict:
    """Submit a single ride request and return the result."""
    payload = {
        "riderId": rider_id,
        "pickupLat": random.uniform(0.0, 1.0),
        "pickupLng": random.uniform(0.0, 1.0),
        "dropoffLat": random.uniform(0.0, 1.0),
        "dropoffLng": random.uniform(0.0, 1.0),
    }

    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        "http://localhost:8080/api/rides",
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST"
    )

    start = time.time()
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            latency = (time.time() - start) * 1000
            body = json.loads(resp.read())
            return {"status": body["status"], "latency_ms": latency, "rider_id": rider_id}
    except urllib.error.HTTPError as e:
        latency = (time.time() - start) * 1000
        if e.code == 503:
            return {"status": "REJECTED", "latency_ms": latency, "rider_id": rider_id}
        return {"status": "ERROR", "latency_ms": latency, "rider_id": rider_id, "code": e.code}
    except Exception as ex:
        return {"status": "ERROR", "latency_ms": 0, "rider_id": rider_id, "error": str(ex)}


def fetch_metrics() -> dict:
    try:
        with urllib.request.urlopen("http://localhost:8080/api/metrics", timeout=3) as resp:
            return json.loads(resp.read())
    except Exception:
        return {}


def print_metrics(m: dict):
    if not m:
        print("  [metrics unavailable]")
        return
    q = m.get("queue", {})
    d = m.get("drivers", {})
    r = m.get("requests", {})
    match = m.get("matching", {})
    print(f"  Queue: {q.get('currentDepth', '?')}/{q.get('capacity', '?')} "
          f"({q.get('fillPercentage', '?')} full) | "
          f"Rejected: {q.get('totalRejected', '?')}")
    print(f"  Drivers: {d.get('available', '?')} available, {d.get('onTrip', '?')} on trip")
    print(f"  Requests: {r.get('matched', '?')} matched, {r.get('queued', '?')} queued, "
          f"{r.get('rejected', '?')} rejected")
    print(f"  Batch efficiency: {match.get('batchEfficiencyPct', '?')} | "
          f"Avg match distance: {match.get('averageMatchDistanceKm', '?')}")


def run_load_test(num_riders: int, delay_between_waves: float):
    print(f"\n{'='*60}")
    print(f"  DispatchIQ Load Test")
    print(f"  Riders: {num_riders} | Workers in system: 5 | Queue cap: 500")
    print(f"{'='*60}\n")

    print("📊 Metrics BEFORE load test:")
    print_metrics(fetch_metrics())
    print()

    # Fire all requests concurrently using a thread pool
    # Max workers = num_riders to truly simulate concurrent clients
    stats = Stats()
    rider_ids = [f"rider-{i:04d}" for i in range(num_riders)]

    print(f"🚀 Firing {num_riders} concurrent ride requests...\n")
    start_time = time.time()

    with ThreadPoolExecutor(max_workers=min(num_riders, 200)) as executor:
        futures = {executor.submit(submit_ride, rid): rid for rid in rider_ids}
        for future in as_completed(futures):
            result = future.result()
            if result["status"] == "QUEUED":
                stats.accepted += 1
                stats.latencies.append(result["latency_ms"])
            elif result["status"] == "REJECTED":
                stats.rejected += 1
            else:
                stats.errors += 1

    total_time = time.time() - start_time

    print(f"✅ All requests sent in {total_time:.2f}s\n")
    print(f"{'='*60}")
    print(f"  SUBMISSION RESULTS")
    print(f"{'='*60}")
    print(f"  Accepted (QUEUED): {stats.accepted}")
    print(f"  Rejected (backpressure): {stats.rejected}")
    print(f"  Errors: {stats.errors}")
    if stats.latencies:
        avg_lat = sum(stats.latencies) / len(stats.latencies)
        print(f"  Avg submission latency: {avg_lat:.1f}ms")
    print()

    # Watch the system drain in real time
    print("⏳ Watching system drain (metrics every 3 seconds)...\n")
    for i in range(10):
        time.sleep(3)
        m = fetch_metrics()
        print(f"[t+{(i+1)*3}s]")
        print_metrics(m)
        print()

        # Stop early if queue is empty and all requests processed
        q = m.get("queue", {})
        if q.get("currentDepth", 1) == 0 and stats.accepted > 0:
            r = m.get("requests", {})
            if (r.get("matched", 0) + r.get("rejected", 0)) >= num_riders:
                print("🎉 Queue fully drained! All requests processed.")
                break

    print(f"{'='*60}")
    print(f"  FINAL METRICS")
    print(f"{'='*60}")
    print_metrics(fetch_metrics())
    print()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="DispatchIQ Load Test")
    parser.add_argument("--riders", type=int, default=500, help="Number of concurrent riders")
    parser.add_argument("--delay", type=float, default=0.0, help="Delay between waves (seconds)")
    args = parser.parse_args()

    run_load_test(args.riders, args.delay)
