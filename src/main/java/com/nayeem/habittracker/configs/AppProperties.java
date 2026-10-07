package com.nayeem.habittracker.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private String name;

    private String backendUrl;

    private String backendUrlShort;

    /** The name users see (emails, Swagger). */
    private String displayName;

    /** Where the web app lives; links in emails point here. No trailing slash. */
    private String frontendUrl;
}
