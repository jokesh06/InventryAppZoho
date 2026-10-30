package com.example.demo.config;

import com.zc.auth.AuthHeaderProvider;
import jakarta.servlet.http.HttpServletRequest;

public class AuthProviderImpl implements AuthHeaderProvider {

    private final HttpServletRequest request;

    public AuthProviderImpl(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public String getHeaderValue(String key) {
        return request.getHeader(key);
    }
}