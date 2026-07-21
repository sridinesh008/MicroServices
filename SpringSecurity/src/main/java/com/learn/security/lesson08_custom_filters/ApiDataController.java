package com.learn.security.lesson08_custom_filters;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/** LESSON 8 — machine-to-machine endpoint, reachable only with ROLE_API. */
@RestController
@Profile("lesson08")
public class ApiDataController {

    @GetMapping("/api/data")
    public String data(Principal principal) {
        return "machine data for " + principal.getName();
    }
}
