package in.spendsmart.expense.service;

import in.spendsmart.expense.entity.Expense;

public interface ExpenseEventPublisher {

    void publishExpenseCreated(Expense expense);
}
