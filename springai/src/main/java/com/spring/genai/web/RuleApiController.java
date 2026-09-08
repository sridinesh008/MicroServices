package com.spring.genai.web;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.spring.genai.rules.CategorizationRule;
import com.spring.genai.rules.CategorizationRuleRepository;
import com.spring.genai.rules.dto.CreateRuleRequest;
import com.spring.genai.rules.dto.RuleView;
import com.spring.genai.users.AppUser;
import com.spring.genai.users.AppUserRepository;

import jakarta.validation.Valid;

@RestController
public class RuleApiController {

	private final CategorizationRuleRepository ruleRepository;
	private final AppUserRepository appUserRepository;

	public RuleApiController(CategorizationRuleRepository ruleRepository, AppUserRepository appUserRepository) {
		this.ruleRepository = ruleRepository;
		this.appUserRepository = appUserRepository;
	}

	@GetMapping("/api/rules")
	public List<RuleView> list(Principal principal) {
		return ruleRepository.findByOwnerOrderByCreatedAtDesc(currentUser(principal)).stream()
				.map(RuleView::from).toList();
	}

	@PostMapping("/api/rules")
	@ResponseStatus(HttpStatus.CREATED)
	public RuleView create(@Valid @RequestBody CreateRuleRequest request, Principal principal) {
		CategorizationRule rule = ruleRepository.save(
				new CategorizationRule(currentUser(principal), request.instructionText(), null));
		return RuleView.from(rule);
	}

	@DeleteMapping("/api/rules/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deactivate(@PathVariable Long id, Principal principal) {
		AppUser user = currentUser(principal);
		CategorizationRule rule = ruleRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such rule."));
		if (!rule.getOwner().getId().equals(user.getId())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such rule.");
		}
		rule.deactivate();
		ruleRepository.save(rule);
	}

	private AppUser currentUser(Principal principal) {
		return appUserRepository.findByUsername(principal.getName())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
	}

}
