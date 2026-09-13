#!/usr/bin/env python3
"""Summarize Maven Surefire XML reports into a small JSON file consumed by
the quarkus-ui "Tests" dashboard tab. Usage:
  generate-test-summary.py <app-name> <surefire-reports-dir> <output-json-path>
"""
import glob
import json
import os
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone


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

    with open(out_path, "w") as fh:
        json.dump(summary, fh, indent=2)

    print(f"Wrote {out_path}: {summary}")


if __name__ == "__main__":
    main()
