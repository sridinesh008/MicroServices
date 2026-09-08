package com.spring.genai.expense;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.spring.genai.users.AppUser;

public interface ExpenseBatchRepository extends JpaRepository<ExpenseBatch, Long> {

	List<ExpenseBatch> findByOwnerAndStatus(AppUser owner, ExpenseStatus status);

}
