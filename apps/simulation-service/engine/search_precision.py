from __future__ import annotations

from decimal import ROUND_HALF_UP, Decimal


COORDINATE_DECIMALS = 4
COORDINATE_QUANTUM = Decimal(1).scaleb(-COORDINATE_DECIMALS)


def decimal_value(value: int | float | str | Decimal) -> Decimal:
    return value if isinstance(value, Decimal) else Decimal(str(value))


def quantized(value: Decimal) -> Decimal:
    return value.quantize(COORDINATE_QUANTUM, rounding=ROUND_HALF_UP)


def preserved_span_bounds(center: float, span: float) -> tuple[float, float]:
    exact_span = quantized(decimal_value(span))
    start = quantized(decimal_value(center) - exact_span / 2)
    return float(start), float(start + exact_span)
