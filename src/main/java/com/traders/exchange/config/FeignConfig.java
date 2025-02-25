package com.traders.exchange.config;

import com.traders.common.properties.ConfigProperties;
import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {

    private final ConfigProperties configProperties;

    public FeignConfig(ConfigProperties configProperties) {
        this.configProperties = configProperties;
    }

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> requestTemplate.header("Authorization", "Bearer " + getAuthToken());
    }

    private String getAuthToken() {
        if(RequestContextHolder
                .getRequestAttributes() == null){
            return configProperties.getSecurity().getAuthentication().getJwt().getMachineToken();
        }
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder
                .getRequestAttributes()).getRequest();
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        return StringUtils.EMPTY;
    }
}
