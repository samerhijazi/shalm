#!/usr/bin/env python3
"""Summarize Maven Surefire XML reports into a small JSON history file
consumed by the quarkus-ui "Tests" dashboard tab. The output is a JSON array
of up to the 10 most recent runs, newest first — each CI run prepends its own
summary to whatever history is already committed at <output-json-path> and
truncates to 10. Usage:
  generate-test-summary.py <app-name> <surefire-reports-dir> <output-json-path>
"""
import glob
import json
import os
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

MAX_HISTORY = 10


def load_history(path: str) -> list:
    if not os.path.exists(path):
        return []
    try:
        with open(path) as fh:
            data = json.load(fh)
    except (OSError, json.JSONDecodeError):
        return []
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        return [data]
    return []


def main() -> None:
    app, reports_dir, out_path = sys.argv[1], sys.argv[2], sys.argv[3]

    tests = failures = errors = skipped = 0
    for report in glob.glob(os.path.join(reports_dir, "TEST-*.xml")):
        root = ET.parse(report).getroot()
        tests += int(root.get("tests", 0))
        failures += int(root.get("failures", 0))
        errors += int(root.get("errors", 0))
        skipped += int(root.get("skipped", 0))

    summary = {
        "app": app,
        "tests": tests,
        "passed": tests - failures - errors - skipped,
        "failures": failures,
        "errors": errors,
        "skipped": skipped,
        "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "commit": os.environ.get("GITHUB_SHA", "")[:7] or None,
    }

    history = load_history(out_path)
    history.insert(0, summary)
    history = history[:MAX_HISTORY]

    with open(out_path, "w") as fh:
        json.dump(history, fh, indent=2)

    print(f"Wrote {out_path} ({len(history)} run(s)): {summary}")


if __name__ == "__main__":
    main()
