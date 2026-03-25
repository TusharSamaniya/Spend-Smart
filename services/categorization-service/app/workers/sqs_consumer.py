from __future__ import annotations

import json
import os
import time

import boto3
import requests

from app.model.predict import predict_category


SQS_QUEUE_URL = os.getenv("SQS_QUEUE_URL", "http://localhost:4566/000000000000/expense-created")
AWS_REGION = os.getenv("AWS_REGION", "ap-south-1")
SQS_ENDPOINT_URL = os.getenv("SQS_ENDPOINT_URL", "http://localhost:4566")
EXPENSE_SERVICE_URL = os.getenv("EXPENSE_SERVICE_URL", "http://expense-service:8081")


def _extract_expense_fields(payload: dict) -> dict:
	expense_data = payload.get("expense", payload)
	return {
		"id": expense_data.get("id"),
		"merchant_name": expense_data.get("merchant_name") or expense_data.get("merchantName") or "",
		"merchant_vpa": expense_data.get("merchant_vpa") or expense_data.get("merchantVpa"),
		"amount": expense_data.get("amount") or 0,
		"payment_method": expense_data.get("payment_method") or expense_data.get("paymentMethod") or "",
		"event_type": payload.get("event_type") or payload.get("eventType") or "expense.created",
	}


def start_consumer() -> None:
	sqs = boto3.client("sqs", region_name=AWS_REGION, endpoint_url=SQS_ENDPOINT_URL)

	while True:
		try:
			response = sqs.receive_message(
				QueueUrl=SQS_QUEUE_URL,
				MaxNumberOfMessages=10,
				WaitTimeSeconds=10,
				VisibilityTimeout=30,
			)
			messages = response.get("Messages", [])
			if not messages:
				continue

			for message in messages:
				receipt_handle = message.get("ReceiptHandle")
				body_raw = message.get("Body", "{}")

				try:
					payload = json.loads(body_raw)
				except json.JSONDecodeError:
					if receipt_handle:
						sqs.delete_message(QueueUrl=SQS_QUEUE_URL, ReceiptHandle=receipt_handle)
					continue

				fields = _extract_expense_fields(payload)
				if fields["event_type"] != "expense.created" or not fields["id"]:
					if receipt_handle:
						sqs.delete_message(QueueUrl=SQS_QUEUE_URL, ReceiptHandle=receipt_handle)
					continue

				prediction = predict_category(
					merchant_name=fields["merchant_name"],
					vpa=fields["merchant_vpa"],
					amount=fields["amount"],
					payment_method=fields["payment_method"],
				)

				top = prediction["top"]
				patch_url = f"{EXPENSE_SERVICE_URL}/v1/expenses/{fields['id']}/category"
				patch_payload = {
					"category_id": top["id"],
					"category_name": top["name"],
					"confidence": top["confidence"],
					"alternatives": prediction.get("alternatives", []),
				}

				patch_response = requests.patch(patch_url, json=patch_payload, timeout=10)
				patch_response.raise_for_status()

				if receipt_handle:
					sqs.delete_message(QueueUrl=SQS_QUEUE_URL, ReceiptHandle=receipt_handle)

		except Exception:
			time.sleep(2)
