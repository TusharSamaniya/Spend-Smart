package in.spendsmart.expenseservice;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
	scanBasePackages = "in.spendsmart.expense",
	exclude = UserDetailsServiceAutoConfiguration.class
)
@EntityScan(basePackages = "in.spendsmart.expense.entity")
@EnableJpaRepositories(basePackages = "in.spendsmart.expense.repository")
public class ExpenseServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExpenseServiceApplication.class, args);
	}

}
