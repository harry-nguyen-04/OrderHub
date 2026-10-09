package vhuwng.orderhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import vhuwng.orderhub.properties.JwtProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class OrderHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderHubApplication.class, args);
    }

}
