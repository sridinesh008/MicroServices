package com.learn.security.lesson06_method_security;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * LESSON 6 — thin controller, ZERO security code.
 *
 * All protection sits on DocumentService. If tomorrow a GraphQL resolver
 * or Kafka listener calls the same service, the rules still apply —
 * that is the point of putting them at the method layer.
 */
@RestController
@Profile("lesson06")
public class DocumentController {

    private final DocumentService service;

    DocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping("/documents")
    List<DocumentService.Document> byOwner(@RequestParam String owner) {
        return service.findByOwner(owner);
    }

    @GetMapping("/documents/{id}")
    DocumentService.Document byId(@PathVariable Long id) {
        return service.findById(id);
    }

    @DeleteMapping("/documents/{id}")
    String delete(@PathVariable Long id) {
        service.delete(id);
        return "deleted " + id;
    }
}
