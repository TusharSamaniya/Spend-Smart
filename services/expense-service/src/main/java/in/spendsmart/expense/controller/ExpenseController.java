package in.spendsmart.expense.controller;

import in.spendsmart.expense.dto.CreateExpenseRequest;
import in.spendsmart.expense.dto.ExpenseResponse;
import in.spendsmart.expense.security.CurrentUser;
import in.spendsmart.expense.service.ExpenseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/expenses")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping("/")
    public ResponseEntity<ExpenseResponse> create(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody CreateExpenseRequest request
    ) {
        ExpenseResponse response = expenseService.create(currentUser.getOrgId(), currentUser.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping({"", "/"})
    public ResponseEntity<List<ExpenseResponse>> list(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestHeader(value = "X-Org-Id", required = false) String forwardedOrgId
    ) {
        log.info("Forwarded X-Org-Id header={}", forwardedOrgId);
        List<ExpenseResponse> response = expenseService.list(currentUser.getOrgId(), currentUser.getUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponse> getById(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable UUID id
    ) {
        ExpenseResponse response = expenseService.getById(id, currentUser.getOrgId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ExpenseResponse> update(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable UUID id,
            @RequestBody CreateExpenseRequest request
    ) {
        ExpenseResponse response = expenseService.update(id, currentUser.getOrgId(), request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDelete(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable UUID id
    ) {
        expenseService.softDelete(id, currentUser.getOrgId());
    }
}
