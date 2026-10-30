
package com.example.demo.config;

import com.zc.auth.CatalystSDK;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CatalystSdkFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Initialize Catalyst SDK only.
        try {
            CatalystSDK.init(new AuthProviderImpl(request));

        } catch (Exception ex) {
            throw new ServletException(
                    "Catalyst SDK initialization failed. " +
                    "Check Catalyst request authentication headers.",
                    ex
            );
        }

        // Continue with Spring Security and Spring MVC.
        // Keep this outside the initialization try-catch.
        filterChain.doFilter(request, response);
    }
}
