package in.spendsmart.expense.service;

import in.spendsmart.expense.dto.CreateExpenseRequest;
import in.spendsmart.expense.dto.ExpenseResponse;
import in.spendsmart.expense.entity.Expense;
import in.spendsmart.expense.event.ExpenseEventPublisher;
import in.spendsmart.expense.exception.ResourceNotFoundException;
import in.spendsmart.expense.repository.ExpenseRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private static final String BASE_CURRENCY = "INR";

    private final ExpenseRepository expenseRepository;
    private final ExpenseEventPublisher eventPublisher;
    private final FxRateService fxRateService;

    public ExpenseResponse create(UUID orgId, UUID userId, CreateExpenseRequest request) {
        BigDecimal fxRate = fxRateService.getRate(request.getCurrency(), BASE_CURRENCY);
        BigDecimal amountBase = request.getAmount()
                .multiply(fxRate)
                .setScale(2, RoundingMode.HALF_UP);

        Expense expense = Expense.builder()
                .id(UUID.randomUUID())
                .orgId(orgId)
                .userId(userId)
                .amount(request.getAmount())
                .currency(normalizeCurrency(request.getCurrency()))
                .amountBase(amountBase)
                .baseCurrency(BASE_CURRENCY)
                .fxRate(fxRate)
                .merchantName(request.getMerchantName())
                .merchantVpa(request.getMerchantVpa())
                .paymentMethod(Expense.PaymentMethod.valueOf(request.getPaymentMethod().toUpperCase(Locale.ROOT)))
                .status(Expense.ExpenseStatus.DRAFT)
                .receiptId(request.getReceiptId())
                .projectId(request.getProjectId())
                .tags(request.getTags() == null ? null : request.getTags().toArray(String[]::new))
                .notes(request.getNotes())
                .expenseDate(request.getExpenseDate())
                .build();

        Expense saved = expenseRepository.save(expense);
        eventPublisher.publishExpenseCreated(saved);
        return mapToResponse(saved);
    }

    public void softDelete(UUID expenseId, UUID orgId) {
        Expense expense = expenseRepository.findByIdAndOrgId(expenseId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found for id: " + expenseId));

        expense.setDeletedAt(OffsetDateTime.now());
        expenseRepository.save(expense);
    }

    public List<ExpenseResponse> list(UUID orgId, UUID userId) {
        return expenseRepository.findByOrgIdAndUserIdOrderByExpenseDateDesc(orgId, userId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private ExpenseResponse mapToResponse(Expense expense) {
        return ExpenseResponse.builder()
                .id(expense.getId())
                .amount(expense.getAmount())
                .currency(expense.getCurrency())
                .amountBase(expense.getAmountBase())
                .baseCurrency(expense.getBaseCurrency())
                .merchantName(expense.getMerchantName())
                .paymentMethod(expense.getPaymentMethod() == null ? null : expense.getPaymentMethod().name())
                .status(expense.getStatus() == null ? null : expense.getStatus().name())
                .expenseDate(expense.getExpenseDate())
                .createdAt(expense.getCreatedAt())
                .tags(expense.getTags() == null ? null : Arrays.asList(expense.getTags()))
                .notes(expense.getNotes())
                .gst(new ExpenseResponse.GstInfo(
                        expense.getCgst(),
                        expense.getSgst(),
                        expense.getIgst(),
                        sumGst(expense)
                ))
                .build();
    }

    private String normalizeCurrency(String currency) {
        return currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal sumGst(Expense expense) {
        BigDecimal cgst = expense.getCgst() == null ? BigDecimal.ZERO : expense.getCgst();
        BigDecimal sgst = expense.getSgst() == null ? BigDecimal.ZERO : expense.getSgst();
        BigDecimal igst = expense.getIgst() == null ? BigDecimal.ZERO : expense.getIgst();
        return cgst.add(sgst).add(igst);
    }
}
