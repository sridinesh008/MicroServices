package com.learn.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ENTRY POINT for the whole course.
 *
 * HOW THIS PROJECT WORKS
 * ----------------------
 * Each lesson lives in its own package (lesson01_defaults, lesson02_basicauth, ...).
 * Each lesson's configuration classes are annotated with @Profile("lessonNN"),
 * so only ONE lesson's security setup is active at a time.
 *
 * Run a lesson like this:
 *
 *   mvn spring-boot:run -Dspring-boot.run.profiles=lesson01
 *
 * or in IntelliJ: Run Configuration -> Active profiles: lesson01
 *
 * The shared controller in the `common` package gives every lesson the same
 * three endpoints to experiment against:
 *
 *   GET /public/hello   - meant to be open to everyone
 *   GET /private/hello  - meant to require login
 *   GET /admin/hello    - meant to require the ADMIN role
 *
 * Whether those intentions are actually enforced depends on the lesson you run.
 * That difference IS the course.
 */
@SpringBootApplication
public class SpringSecurityLearningApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringSecurityLearningApplication.class, args);
    }
}
