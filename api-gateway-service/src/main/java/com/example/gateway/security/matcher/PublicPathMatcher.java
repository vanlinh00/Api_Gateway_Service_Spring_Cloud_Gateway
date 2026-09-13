package com.example.gateway.security.matcher;

import com.example.gateway.config.GatewaySecurityProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * High-performance AntPathMatcher wrapper for whitelisting public Gateway routes.
 */
@Component
public class PublicPathMatcher {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final GatewaySecurityProperties properties;

    public PublicPathMatcher(GatewaySecurityProperties properties) {
        this.properties = properties;
    }

    /**
     * Determines whether the given HTTP path matches any configured excluded public path.
     */
    public boolean isPublic(String requestPath) {
        if (!StringUtils.hasText(requestPath)) {
            return false;
        }

        List<String> excluded = properties.getExcludedPaths();
        if (excluded == null || excluded.isEmpty()) {
            return false;
        }

        for (String pattern : excluded) {
            if (pathMatcher.match(pattern, requestPath)) {
                return true;
            }
        }
        return false;
    }
}
