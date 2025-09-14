package de.tub;

import lombok.AllArgsConstructor;
import lombok.Getter;
@Getter
@AllArgsConstructor

public class AuthorizedUsers { 
        private final String login;
        private final String password;
        private final String role; // admin or seller

        public AuthorizedUsers(String login, String role) {
        this.login = login;
        this.password = null;
        this.role = role;
    }

    }

