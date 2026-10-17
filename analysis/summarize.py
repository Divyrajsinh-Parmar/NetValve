#!/usr/bin/env python3
"""Summarise PayProbe CSV files (and nothing else): median, p95, p99, max, mean, failures.

Usage:
    python3 analysis/summarize.py docs/data/runs/            # every payprobe_*.csv in a folder
    python3 analysis/summarize.py a.csv b.csv --md           # Markdown table for the report
    python3 analysis/summarize.py runs/ --by label           # pool all files that share a label
    python3 analysis/summarize.py runs/ --from-sec 10 --to-sec 70   # only seconds 10 to 70 of every run

Percentiles use the nearest-rank method, exactly like the app, and failures (timeouts stored
as 5000 ms) are INCLUDED, because a request that waited five seconds was delayed five seconds.
Standard library only, so it runs on any laptop.
"""
import argparse
import csv
import glob
import math
import os
import sys


def percentile(sorted_vals, p):
    if not sorted_vals:
        return 0
    rank = min(max(math.ceil(p / 100.0 * len(sorted_vals)), 1), len(sorted_vals))
    return sorted_vals[rank - 1]


def read_rows(path):
    with open(path, newline="") as f:
        return list(csv.DictReader(f))


def summarize(rows):
    lat = sorted(int(r["latency_ms"]) for r in rows)
    fails = sum(1 for r in rows if r["ok"] != "1")
    return {
        "n": len(lat),
        "fails": fails,
        "p50": percentile(lat, 50),
        "p95": percentile(lat, 95),
        "p99": percentile(lat, 99),
        "max": lat[-1] if lat else 0,
        "mean": (sum(lat) / len(lat)) if lat else 0.0,
    }


def collect(paths):
    files = []
    for p in paths:
        if os.path.isdir(p):
            files += sorted(glob.glob(os.path.join(p, "**", "*.csv"), recursive=True))
        else:
            files.append(p)
    return files


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("paths", nargs="+", help="CSV files or folders")
    ap.add_argument("--by", choices=["file", "label"], default="file", help="one row per file (default) or per label")
    ap.add_argument("--md", action="store_true", help="print a Markdown table")
    ap.add_argument("--skip", type=int, default=0, help="ignore the first N samples of every run (warm-up)")
    ap.add_argument("--from-sec", type=float, default=None,
                    help="keep only samples at or after this many seconds since the run started (elapsed_ms)")
    ap.add_argument("--to-sec", type=float, default=None,
                    help="keep only samples before this many seconds since the run started (elapsed_ms)")
    args = ap.parse_args(argv)

    files = collect(args.paths)
    if not files:
        print("no CSV files found", file=sys.stderr)
        return 1

    groups = {}
    for path in files:
        rows = read_rows(path)
        # The time window comes first (it cuts by elapsed time), then --skip drops the first N of what is left.
        if args.from_sec is not None:
            rows = [r for r in rows if int(r["elapsed_ms"]) >= args.from_sec * 1000]
        if args.to_sec is not None:
            rows = [r for r in rows if int(r["elapsed_ms"]) < args.to_sec * 1000]
        rows = rows[args.skip:]
        if not rows:
            continue
        key = os.path.basename(path) if args.by == "file" else rows[0].get("label", "?")
        groups.setdefault(key, []).extend(rows)

    header = ["run", "n", "fails", "p50", "p95", "p99", "max", "mean"]
    lines = []
    for key in sorted(groups):
        s = summarize(groups[key])
        lines.append([key, s["n"], s["fails"], s["p50"], s["p95"], s["p99"], s["max"], f'{s["mean"]:.0f}'])

    if args.md:
        print("| " + " | ".join(header) + " |")
        print("|" + "|".join(["---"] * len(header)) + "|")
        for l in lines:
            print("| " + " | ".join(str(x) for x in l) + " |")
    else:
        widths = [max(len(str(x)) for x in col) for col in zip(header, *lines)]
        fmt = "  ".join("{:<%d}" % w for w in widths)
        print(fmt.format(*header))
        for l in lines:
            print(fmt.format(*[str(x) for x in l]))
    print("\nall values in ms; failures are included in the percentiles", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
