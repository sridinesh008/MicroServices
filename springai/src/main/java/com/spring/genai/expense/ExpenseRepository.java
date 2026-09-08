package com.spring.genai.expense;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.spring.genai.users.AppUser;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

	List<Expense> findByOwnerAndStatusOrderByCreatedAtDesc(AppUser owner, ExpenseStatus status);

	List<Expense> findByOwnerAndStatusAndExpenseYearAndExpenseMonth(AppUser owner, ExpenseStatus status,
			int expenseYear, int expenseMonth);

	@Query("select distinct e.category from Expense e where e.owner = :owner and e.status = :status order by e.category")
	List<String> findDistinctCategories(AppUser owner, ExpenseStatus status);

}
