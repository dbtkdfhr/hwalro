"""Deterministic builder for the synthetic stall-family fixture.

Synthetic equivalent candidate for the shared-target stall family studied in
the Gate4-E2 design (the external fixture #13 analog). Coordinates are drawn
from a fixed-seed RNG and snapped to the 0.25 m routing grid, so the payload
is deterministic and portable.

Naming policy (design REV-4 #3): this fixture is synthetic and is NOT called
"exact #13".

Approval status (plan P1-10): NEGATIVE-ONLY. Approval condition 1 (a
flag=false shared-target three-agent STALLED reproduction) is not met by this
synthetic geometry, so the fixture is not adopted as the repository
functional recovery fixture. It is a deterministic negative stall-family
fixture used for flag=false parity and recovery-flag plumbing smoke tests.
The functionalGate stays undetermined until the external exact #13 staging
run.
"""

from __future__ import annotations

import argparse
import json
import random
from pathlib import Path
from typing import Any

FIXED_SEED = 20260817
GRID_STEP = 0.25
DOOR_GRID_STEP = 0.05

MODEL = {
    "modelProfile": "SFM_DEFAULT_V2",
    "routingProfile": "HAZARD_RADIAL_EXP_V3",
    "walkingSpeed": 1.25,
    "reactionTime": 0.5,
}


def _snap(value: float, grid: float = GRID_STEP) -> float:
    return round(round(value / grid) * grid, 6)


def build_payload() -> dict[str, Any]:
    """Return the deterministic synthetic stall-family input payload."""
    rng = random.Random(FIXED_SEED)

    room_width = _snap(rng.uniform(9.8, 10.2))
    room_height = _snap(rng.uniform(9.8, 10.2))
    door_width = _snap(rng.uniform(0.68, 0.72), DOOR_GRID_STEP)
    door_center = _snap(room_height / 2.0)
    door_start = round(door_center - door_width / 2.0, 6)
    door_end = round(door_center + door_width / 2.0, 6)
    row_x = _snap(rng.uniform(8.9, 9.3))
    row_spread = _snap(rng.uniform(0.42, 0.55))
    back_x = round(row_x + rng.uniform(0.32, 0.42), 6)

    agents = [
        {"x": row_x, "y": round(door_center - row_spread, 6)},
        {"x": row_x, "y": round(door_center, 6)},
        {"x": row_x, "y": round(door_center + row_spread, 6)},
        {"x": back_x, "y": round(door_center, 6)},
    ]
    return {
        "model": dict(MODEL),
        "drawing": {
            "outsideBoundary": [
                {"x": 0.0, "y": 0.0},
                {"x": room_width, "y": 0.0},
                {"x": room_width, "y": room_height},
                {"x": 0.0, "y": room_height},
            ],
            "walls": [],
            "pillars": [],
            "fabrics": [],
            "exits": [
                {
                    "id": 1,
                    "startX": room_width,
                    "startY": door_start,
                    "endX": room_width,
                    "endY": door_end,
                }
            ],
        },
        "agents": agents,
        "hazards": [],
        "selectedExitIds": [1],
        "maxSimulationTimeSeconds": 30,
        "frameIntervalSeconds": 1,
    }


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "output",
        nargs="?",
        type=Path,
        help="output JSON path (default: stdout)",
    )
    args = parser.parse_args(argv)
    payload = build_payload()
    if args.output:
        args.output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    else:
        print(json.dumps(payload, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
