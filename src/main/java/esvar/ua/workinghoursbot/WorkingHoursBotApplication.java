package esvar.ua.workinghoursbot;

import esvar.ua.workinghoursbot.config.BotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(BotProperties.class)
public class WorkingHoursBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkingHoursBotApplication.class, args);
    }
}
