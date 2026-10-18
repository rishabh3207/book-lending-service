package com.rishabh.booklending.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "library.security")
public record SecurityUsersProperties(List<UserEntry> users) {

    public record UserEntry(String username, String password, List<String> roles) {
    }
}