from __future__ import annotations

from typing import Any

from app.model.rule_based import categorize_by_rules


_CATEGORY_IDS = {
	"Food": 1,
	"Food Delivery": 2,
	"Groceries": 3,
	"Fuel": 4,
	"Pharmacy": 5,
	"Healthcare": 6,
	"Transport": 7,
	"Travel": 8,
	"Utilities": 9,
	"Telecom": 10,
	"Shopping": 11,
	"Entertainment": 12,
	"Insurance": 13,
	"Education": 14,
	"Government": 15,
	"Home Services": 16,
	"Others": 99,
}


_MERCHANT_HINTS = {
	"Food Delivery": ["swiggy", "zomato", "blinkit", "zepto", "eat"],
	"Groceries": ["mart", "grocery", "basket", "fresh"],
	"Fuel": ["fuel", "petrol", "diesel", "pump", "hpcl", "bpcl", "iocl"],
	"Pharmacy": ["pharmacy", "apollo", "medplus", "1mg", "netmeds"],
	"Transport": ["uber", "ola", "rapido", "metro", "toll", "fastag"],
	"Travel": ["trip", "flight", "train", "hotel", "irctc", "oyo"],
	"Utilities": ["electricity", "water", "gas", "bill", "recharge"],
	"Shopping": ["amazon", "flipkart", "myntra", "nykaa", "ajio"],
	"Entertainment": ["netflix", "hotstar", "spotify", "prime", "movie"],
}

_METHOD_ALTERNATIVES = {
	"upi": ["Food Delivery", "Groceries", "Utilities", "Transport"],
	"cash": ["Food", "Groceries", "Transport", "Shopping"],
	"card": ["Shopping", "Fuel", "Entertainment", "Travel"],
	"credit_card": ["Shopping", "Fuel", "Entertainment", "Travel"],
	"debit_card": ["Shopping", "Fuel", "Entertainment", "Travel"],
	"netbanking": ["Utilities", "Insurance", "Government", "Travel"],
}


def _format_category(category_id: int, category_name: str, confidence: float) -> dict[str, Any]:
	return {
		"id": category_id,
		"name": category_name,
		"confidence": round(float(confidence), 2),
	}


def _build_alternatives(
	merchant_name: str,
	payment_method: str,
	top_name: str,
	top_confidence: float,
) -> list[dict[str, Any]]:
	candidates: list[tuple[str, float]] = []
	merchant_normalized = (merchant_name or "").strip().lower()
	payment_method_normalized = (payment_method or "").strip().lower()

	for category_name, hints in _MERCHANT_HINTS.items():
		if category_name == top_name:
			continue
		if any(hint in merchant_normalized for hint in hints):
			candidates.append((category_name, min(top_confidence - 0.05, 0.65)))

	for category_name in _METHOD_ALTERNATIVES.get(payment_method_normalized, []):
		if category_name != top_name:
			candidates.append((category_name, min(top_confidence - 0.10, 0.55)))

	deduped: list[tuple[str, float]] = []
	seen: set[str] = set()
	for category_name, confidence in candidates:
		if category_name in seen:
			continue
		seen.add(category_name)
		adjusted_confidence = max(0.10, min(confidence, top_confidence - 0.01))
		deduped.append((category_name, adjusted_confidence))
		if len(deduped) == 3:
			break

	formatted: list[dict[str, Any]] = []
	for category_name, confidence in deduped:
		formatted.append(
			_format_category(
				_CATEGORY_IDS.get(category_name, _CATEGORY_IDS["Others"]),
				category_name,
				confidence,
			)
		)

	return formatted


def predict_category(merchant_name, vpa, amount, payment_method):
	base_prediction = categorize_by_rules(merchant_name, vpa, amount, payment_method)

	top = _format_category(
		base_prediction["category_id"],
		base_prediction["category_name"],
		base_prediction["confidence"],
	)

	alternatives = _build_alternatives(
		merchant_name=merchant_name,
		payment_method=payment_method,
		top_name=top["name"],
		top_confidence=top["confidence"],
	)

	return {
		"top": top,
		"alternatives": alternatives,
	}
