package vhuwng.orderhub.middleware.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String previousCorrelationId = MDC.get(MDC_KEY);
        String correlationId = request.getHeader(HEADER_NAME);

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);
        try{
            filterChain.doFilter(request, response);
        }finally {
            if (previousCorrelationId == null) {
                MDC.remove(MDC_KEY);
            }
            else {
                MDC.put(MDC_KEY, previousCorrelationId);
            }
        }
    }
}
