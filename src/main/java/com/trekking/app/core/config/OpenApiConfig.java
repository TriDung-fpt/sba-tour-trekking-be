package com.trekking.app.core.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
    @Bean
    SwaggerIndexTransformer swaggerIndexTransformer(SwaggerUiConfigProperties config,
            SwaggerUiOAuthProperties oauth, SwaggerWelcomeCommon welcome, ObjectMapperProvider mapper) {
        return new SwaggerIndexPageTransformer(config, oauth, welcome, mapper) {
            @Override
            protected String addCSRF(String html) {
                // Springdoc normally copies an existing cookie, which fails on
                // the first POST and after authentication clears that cookie.
                String interceptor = """
                        requestInterceptor: async (request) => {
                            const url = new URL(request.url, window.location.origin);
                            const authPath = '/api/v1/auth/';
                            const index = url.pathname.lastIndexOf(authPath);
                            const method = (request.method || 'GET').toUpperCase();
                            if (url.origin === window.location.origin && index >= 0
                                    && !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
                                const csrfUrl = new URL(url.href);
                                csrfUrl.pathname = url.pathname.substring(0, index) + authPath + 'csrf';
                                csrfUrl.search = '';
                                csrfUrl.hash = '';
                                const response = await fetch(csrfUrl, {
                                    credentials: 'same-origin', cache: 'no-store',
                                    headers: { 'Accept': 'application/json' }
                                });
                                if (!response.ok) throw new Error('Unable to initialize CSRF protection');
                                const csrf = await response.json();
                                request.headers = request.headers || {};
                                request.headers[csrf.headerName] = csrf.token;
                                request.credentials = 'same-origin';
                            }
                            return request;
                        },
                        presets: [
                        """;
                return html.replace("presets: [", interceptor);
            }
        };
    }
}
