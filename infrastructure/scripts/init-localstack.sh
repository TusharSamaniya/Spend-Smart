#!/bin/bash
# =============================================================================
# LocalStack Initializer
# Creates all required SQS queues and SNS topics on LocalStack startup.
# Uses awslocal (pre-configured alias for aws --endpoint-url=http://localhost:4566).
# =============================================================================

set -euo pipefail

LOCALSTACK_HOST="${LOCALSTACK_HOST:-localhost}"
LOCALSTACK_URL="http://${LOCALSTACK_HOST}:4566"
REGION="ap-south-1"

export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION="$REGION"

# awslocal is the LocalStack CLI wrapper; fall back to plain aws with endpoint-url
# so the same script works whether awslocal is installed or not.
if command -v awslocal &>/dev/null; then
  AWS="awslocal"
else
  AWS="aws --endpoint-url=$LOCALSTACK_URL"
fi

echo "==========================================================="
echo "  LocalStack Initializer"
echo "  Endpoint : $LOCALSTACK_URL"
echo "  Region   : $REGION"
echo "  CLI      : $AWS"
echo "==========================================================="

# ---------------------------------------------------------------------------
# Helper: create SQS queue (idempotent — does nothing if it already exists)
# ---------------------------------------------------------------------------
create_queue() {
  local name="$1"
  shift
  echo -n "  SQS queue: $name ... "
  $AWS sqs create-queue \
    --queue-name "$name" \
    --region "$REGION" \
    "$@" \
    --output text --query 'QueueUrl' 2>/dev/null && echo "OK" || echo "already exists / OK"
}

# ---------------------------------------------------------------------------
# Helper: create SNS topic (idempotent)
# ---------------------------------------------------------------------------
create_topic() {
  local name="$1"
  echo -n "  SNS topic: $name ... "
  $AWS sns create-topic \
    --name "$name" \
    --region "$REGION" \
    --output text --query 'TopicArn' 2>/dev/null && echo "OK" || echo "already exists / OK"
}

# ---------------------------------------------------------------------------
# Wait for LocalStack SQS to be available (extra guard on top of healthcheck)
# ---------------------------------------------------------------------------
echo ""
echo "--- Waiting for LocalStack SQS to be ready ---"
for i in $(seq 1 30); do
  if $AWS sqs list-queues --region "$REGION" &>/dev/null; then
    echo "LocalStack SQS is ready."
    break
  fi
  echo "  Attempt $i/30 — not ready yet, waiting 2s..."
  sleep 2
done

# ---------------------------------------------------------------------------
# SQS FIFO Queues
# ---------------------------------------------------------------------------
echo ""
echo "--- Creating SQS FIFO queues ---"
create_queue "expense-created.fifo" \
  --attributes FifoQueue=true,ContentBasedDeduplication=true

# ---------------------------------------------------------------------------
# SQS Standard Queues
# ---------------------------------------------------------------------------
echo ""
echo "--- Creating SQS standard queues ---"
create_queue "ocr-queue"
create_queue "upi-events"
create_queue "notifications"
create_queue "expense-submitted"
create_queue "expense-events"

# ---------------------------------------------------------------------------
# SNS Topics
# ---------------------------------------------------------------------------
echo ""
echo "--- Creating SNS topics ---"
create_topic "expense-events"
create_topic "receipt-events"
create_topic "anomaly-alerts"

# ---------------------------------------------------------------------------
# Confirmation listing
# ---------------------------------------------------------------------------
echo ""
echo "--- Current SQS queues ---"
$AWS sqs list-queues --region "$REGION" --output table 2>/dev/null || true

echo ""
echo "--- Current SNS topics ---"
$AWS sns list-topics --region "$REGION" --output table 2>/dev/null || true

echo ""
echo "==========================================================="
echo "  LocalStack initialization complete."
echo "==========================================================="
