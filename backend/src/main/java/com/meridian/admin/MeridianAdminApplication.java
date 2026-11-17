package com.meridian.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MeridianAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeridianAdminApplication.class, args);
    }
}
