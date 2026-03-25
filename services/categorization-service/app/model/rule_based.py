from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path


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

_KEYWORD_CATEGORIES = {
	"Food Delivery": [
		"swiggy",
		"zomato",
		"blinkit",
		"zepto",
		"eatclub",
		"dominos",
		"pizza",
		"kfc",
		"burger king",
	],
	"Groceries": [
		"bigbasket",
		"jiomart",
		"dmart",
		"reliance fresh",
		"grocery",
	],
	"Fuel": ["bpcl", "hpcl", "iocl", "indianoil", "petrol", "diesel", "shell"],
	"Pharmacy": ["apollo pharmacy", "medplus", "1mg", "netmeds", "pharmeasy", "pharmacy"],
	"Transport": ["ola", "uber", "rapido", "metro", "fastag", "toll", "cab"],
	"Travel": ["makemytrip", "goibibo", "yatra", "ixigo", "irctc", "air", "hotel", "oyo"],
	"Utilities": ["electricity", "water", "gas", "bill", "bescom", "bses", "torrent power"],
	"Telecom": ["jio", "airtel", "vi", "bsnl", "recharge", "mobile"],
	"Shopping": ["amazon", "flipkart", "myntra", "ajio", "nykaa", "croma", "vijay sales"],
	"Entertainment": ["netflix", "hotstar", "spotify", "prime", "zee5", "sonyliv"],
	"Insurance": ["lic", "insurance", "policy", "hdfc life", "sbi life", "acko"],
	"Education": ["byju", "unacademy", "vedantu", "upgrad", "physics wallah", "tuition"],
}


def _load_vpa_merchants() -> dict[str, dict[str, str]]:
	data_file = Path(__file__).resolve().parent.parent / "data" / "vpa_merchants.json"
	if not data_file.exists():
		return {}
	try:
		return json.loads(data_file.read_text(encoding="utf-8"))
	except (json.JSONDecodeError, OSError):
		return {}


_VPA_MERCHANTS = _load_vpa_merchants()


def _category_payload(category_name: str, confidence: float) -> dict[str, object]:
	return {
		"category_id": _CATEGORY_IDS.get(category_name, _CATEGORY_IDS["Others"]),
		"category_name": category_name,
		"confidence": round(confidence, 2),
	}


def categorize_by_rules(merchant_name, vpa, amount, payment_method):
	merchant_name_normalized = (merchant_name or "").strip().lower()
	vpa_normalized = (vpa or "").strip().lower()
	payment_method_normalized = (payment_method or "").strip().lower()

	# Layer 1: Exact VPA pattern lookup from merchant map.
	vpa_record = _VPA_MERCHANTS.get(vpa_normalized)
	if vpa_record and vpa_record.get("category"):
		return _category_payload(vpa_record["category"], 0.95)

	# Layer 2: Merchant name keyword matching.
	for category_name, keywords in _KEYWORD_CATEGORIES.items():
		if any(keyword in merchant_name_normalized for keyword in keywords):
			return _category_payload(category_name, 0.75)

	# Layer 3: Payment method heuristics.
	try:
		amount_value = float(amount)
	except (TypeError, ValueError):
		amount_value = 0.0

	if payment_method_normalized == "cash" and 0 < amount_value < 500:
		return _category_payload("Food", 0.50)

	current_hour = datetime.now().hour
	is_night = current_hour >= 20 or current_hour <= 5
	if payment_method_normalized == "upi" and vpa_normalized and not vpa_record and is_night:
		return _category_payload("Food Delivery", 0.50)

	return _category_payload("Others", 0.40)
