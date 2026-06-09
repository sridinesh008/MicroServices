package com.ratelimiter.admin;

import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.rule.RateLimitRuleRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/rules")
public class RuleController {

    private final RateLimitRuleRepository repository;

    public RuleController(RateLimitRuleRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<RateLimitRule> listAll() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<RateLimitRule> getOne(@PathVariable String id) {
        return repository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<RateLimitRule> create(@Valid @RequestBody RateLimitRule rule) {
        return ResponseEntity.status(201).body(repository.save(rule));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RateLimitRule> update(@PathVariable String id,
                                                @Valid @RequestBody RateLimitRule rule) {
        if (repository.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        // path id is authoritative — overwrite body's ruleId to prevent mismatch
        RateLimitRule updated = new RateLimitRule(id, rule.endpointPattern(), rule.userTier(),
            rule.algorithmType(), rule.capacity(), rule.priority(),
            rule.refillRatePerSecond(), rule.scope(), rule.enabled());
        return ResponseEntity.ok(repository.save(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (repository.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        repository.delete(id);
        return ResponseEntity.noContent().build();
    }
}
