package dev.filebridge;

import dev.filebridge.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackageClasses = AppProperties.class)
public class FileBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileBridgeApplication.class, args);
    }
}
