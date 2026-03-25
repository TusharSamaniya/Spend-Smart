from __future__ import annotations

import json
import threading
from pathlib import Path

from fastapi import FastAPI

from app.routes.categorize import router as categorize_router
from app.routes.health import router as health_router
from app.workers.sqs_consumer import start_consumer


VPA_MERCHANTS: dict[str, dict] = {}
consumer_thread: threading.Thread | None = None


app = FastAPI(title="SpendSmart Categorization Service", version="1.0.0")

app.include_router(health_router)
app.include_router(categorize_router)


@app.on_event("startup")
def load_vpa_merchants() -> None:
	global VPA_MERCHANTS, consumer_thread

	data_file = Path(__file__).resolve().parent / "data" / "vpa_merchants.json"
	try:
		VPA_MERCHANTS = json.loads(data_file.read_text(encoding="utf-8"))
	except (FileNotFoundError, json.JSONDecodeError, OSError):
		VPA_MERCHANTS = {}

	if consumer_thread is None or not consumer_thread.is_alive():
		consumer_thread = threading.Thread(target=start_consumer, daemon=True, name="sqs-consumer")
		consumer_thread.start()

