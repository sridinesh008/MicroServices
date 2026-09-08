package com.spring.genai.rules;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.spring.genai.users.AppUser;

public interface CategorizationRuleRepository extends JpaRepository<CategorizationRule, Long> {

	List<CategorizationRule> findByOwnerAndActiveTrue(AppUser owner);

	List<CategorizationRule> findByOwnerOrderByCreatedAtDesc(AppUser owner);

}
