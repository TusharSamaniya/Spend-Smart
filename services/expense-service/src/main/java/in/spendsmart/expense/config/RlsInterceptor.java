package in.spendsmart.expense.config;

import in.spendsmart.expense.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class RlsInterceptor implements HandlerInterceptor {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            return true;
        }

        UUID orgId = currentUser.getOrgId();
        if (orgId != null) {
            jdbcTemplate.execute("SET LOCAL app.org_id = '" + orgId + "'");
        }

        return true;
    }
}
