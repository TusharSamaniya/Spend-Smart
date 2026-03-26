package in.spendsmart.workflowservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.spendsmart.workflowservice.model.ExpenseSubmittedEvent;
import in.spendsmart.workflowservice.service.WorkflowService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExpenseSubmittedListener {

    private final ObjectMapper objectMapper;
    private final WorkflowService workflowService;

    @SqsListener("${aws.sqs.expense-submitted-queue-url}")
    public void onExpenseSubmitted(String messageBody) {
        try {
            ExpenseSubmittedEvent event = objectMapper.readValue(messageBody, ExpenseSubmittedEvent.class);
            workflowService.onExpenseSubmitted(event);
        } catch (Exception exception) {
            log.error("Failed to process expense-submitted SQS message: {}", messageBody, exception);
        }
    }
}
