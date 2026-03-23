#!/usr/bin/env bash

set -euo pipefail

ENDPOINT_URL="http://localhost:4566"
REGION="ap-south-1"

echo "Creating S3 bucket: spendsmart-receipts"
aws --endpoint-url="$ENDPOINT_URL" --region "$REGION" s3api create-bucket \
  --bucket spendsmart-receipts

echo "Creating SQS queue: expense-created.fifo"
aws --endpoint-url="$ENDPOINT_URL" --region "$REGION" sqs create-queue \
  --queue-name expense-created.fifo \
  --attributes FifoQueue=true,ContentBasedDeduplication=true

echo "Creating SQS queue: ocr-queue"
aws --endpoint-url="$ENDPOINT_URL" --region "$REGION" sqs create-queue \
  --queue-name ocr-queue

echo "Creating SQS queue: upi-events"
aws --endpoint-url="$ENDPOINT_URL" --region "$REGION" sqs create-queue \
  --queue-name upi-events

echo "Creating SQS queue: notifications"
aws --endpoint-url="$ENDPOINT_URL" --region "$REGION" sqs create-queue \
  --queue-name notifications

echo "Creating SNS topic: expense-events"
aws --endpoint-url="$ENDPOINT_URL" --region "$REGION" sns create-topic \
  --name expense-events

echo "Creating SNS topic: receipt-events"
aws --endpoint-url="$ENDPOINT_URL" --region "$REGION" sns create-topic \
  --name receipt-events

echo "LocalStack resource setup complete."