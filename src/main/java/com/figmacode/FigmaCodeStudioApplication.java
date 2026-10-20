package com.figmacode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FigmaCodeStudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(FigmaCodeStudioApplication.class, args);
    }
}
