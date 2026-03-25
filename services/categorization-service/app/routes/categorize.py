from fastapi import APIRouter
from pydantic import BaseModel

from app.model.predict import predict_category


router = APIRouter()


class CategorizeRequest(BaseModel):
	merchant_name: str
	merchant_vpa: str | None = None
	amount: float
	payment_method: str


class CategorizeResponse(BaseModel):
	category: dict
	confidence: float
	alternatives: list


@router.post("/v1/categorize", response_model=CategorizeResponse)
def categorize_transaction(request: CategorizeRequest) -> CategorizeResponse:
	prediction = predict_category(
		merchant_name=request.merchant_name,
		vpa=request.merchant_vpa,
		amount=request.amount,
		payment_method=request.payment_method,
	)

	top = prediction["top"]
	return CategorizeResponse(
		category={"id": top["id"], "name": top["name"]},
		confidence=top["confidence"],
		alternatives=prediction.get("alternatives", []),
	)
