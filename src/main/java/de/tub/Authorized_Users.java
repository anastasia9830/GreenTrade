package de.tub;

import lombok.AllArgsConstructor;
import lombok.Getter;
@Getter
@AllArgsConstructor

public class Authorized_Users { // can be record class
        private final String login;
        private final String password;
        private final String role; // admin or seller
    }

