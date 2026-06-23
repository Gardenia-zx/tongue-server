package com.tongue.server;

import com.tongue.server.config.AgentProperties;
import com.tongue.server.config.AsyncProperties;
import com.tongue.server.config.AuthProperties;
import com.tongue.server.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        AgentProperties.class,
        AsyncProperties.class,
        AuthProperties.class,
        StorageProperties.class
})
public class TongueServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TongueServerApplication.class, args);
    }
}
