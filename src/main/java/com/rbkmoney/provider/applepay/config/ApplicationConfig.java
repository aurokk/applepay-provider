package com.rbkmoney.provider.applepay.config;

import com.rbkmoney.damsel.payment_tool_provider.PaymentToolProviderSrv;
import com.rbkmoney.provider.applepay.iface.decrypt.ProviderHandler;
import com.rbkmoney.provider.applepay.service.DecryptionService;
import com.rbkmoney.provider.applepay.service.SSLProvider;
import com.rbkmoney.provider.applepay.service.SessionService;
import com.rbkmoney.provider.applepay.service.SignatureValidator;
import com.rbkmoney.provider.applepay.store.APCertStore;
import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Created by vpankrashkin on 10.04.18.
 */
@Configuration
public class ApplicationConfig {

    @Bean
    public APCertStore certStore(@Value("${cert.base:@null}") String baseDir, @Value("${cert.identity.path:@null}") String identityDir, @Value("${cert.processing.path:@null}") String processingDir) {
        if (baseDir == null && identityDir == null && processingDir == null) {
            throw new IllegalArgumentException("At least base cert dir must be specified");
        }
        if (identityDir != null && processingDir != null) {
            return new APCertStore(identityDir, processingDir);
        }

        return new APCertStore(baseDir);
    }

    @Bean
    public SessionService sessionService(APCertStore certStore,
                                         @Value("${cert.identity.pass}") String identityPass,
                                         @Value("${apple.conn_timeout}") int connTimeoutMs,
                                         @Value("${apple.read_timeout}") int readTimeoutMs,
                                         @Value("${apple.write_timeout}") int writeTimeoutMs) {
        return new SessionService(certStore, identityPass, connTimeoutMs, readTimeoutMs, writeTimeoutMs);
    }

    @Bean
    public SignatureValidator signatureValidator(@Value("${cert.ca.path}") Resource resource, @Value("${apple.expiration_time}") Long expirationTime) throws IOException {
        return new SignatureValidator(Files.readAllBytes(resource.getFile().toPath()), expirationTime);
    }

    @Bean
    public DecryptionService decryptionService(APCertStore apCertStore, @Value("${cert.key.pass}") char[] keyPass) {
        return new DecryptionService(apCertStore, keyPass);
    }

    @Bean
    public PaymentToolProviderSrv.Iface providerHandler(SignatureValidator validator, DecryptionService decryptionService) {
       return new ProviderHandler(validator, decryptionService);
    }

    @Bean
    public ServletWebServerFactory servletContainer(@Value("${server.http_port}") int httpPort) {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();
        Connector connector = new Connector();
        connector.setPort(httpPort);
        tomcat.addAdditionalTomcatConnectors(connector);
        return tomcat;
    }

    @Bean
    public FilterRegistrationBean externalPortRestrictingFilter(@Value("${server.http_port}") int httpPort, @Value("/${server.http_path_prefix}/") String httpPathPrefix) {
        Filter filter = new OncePerRequestFilter() {

            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                if (request.getLocalPort() == httpPort) {
                    if (!request.getServletPath().startsWith(httpPathPrefix)) {
                        response.sendError(404, "Unknown address");
                        return;
                    }
                }
                filterChain.doFilter(request, response);
            }
        };

        FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean();
        filterRegistrationBean.setFilter(filter);
        filterRegistrationBean.setOrder(-100);
        filterRegistrationBean.setName("httpPortFilter");
        filterRegistrationBean.addUrlPatterns("/*");
        return filterRegistrationBean;
    }
}
