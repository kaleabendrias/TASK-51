package com.booking.config;

import com.booking.filter.AuthFilter;
import com.booking.filter.CsrfFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }

    @Bean
    public FilterRegistrationBean<CsrfFilter> csrfFilterRegistration(CsrfFilter csrfFilter) {
        FilterRegistrationBean<CsrfFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(csrfFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(0);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AuthFilter> authFilterRegistration(AuthFilter authFilter) {
        FilterRegistrationBean<AuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(authFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }
}
