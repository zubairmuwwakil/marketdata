package com.zubairmuwwakil.marketdata.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Binds any caller-supplied upstream provider keys to the serving thread for the
 * life of the request, and unbinds them afterwards no matter how the request ends.
 *
 * <p>The header value is never logged or echoed. Runs after authentication so an
 * unauthenticated caller cannot spend a credential.
 */
public class ProviderKeyFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ProviderCredentials.bind(ProviderCredentials.parse(request.getHeader(ProviderCredentials.HEADER)));
        try {
            filterChain.doFilter(request, response);
        } finally {
            ProviderCredentials.clear();
        }
    }
}
