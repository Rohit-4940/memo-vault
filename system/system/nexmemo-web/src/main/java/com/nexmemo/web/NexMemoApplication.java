package com.nexmemo.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "com.nexmemo")
@EnableJpaRepositories(basePackages = "com.nexmemo")
@EntityScan(basePackages = "com.nexmemo")
@EnableAsync
public class NexMemoApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(NexMemoApplication.class, args);
    }
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(NexMemoApplication.class);
    }
}
