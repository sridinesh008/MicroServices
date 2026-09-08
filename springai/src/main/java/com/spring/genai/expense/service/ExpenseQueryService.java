package com.spring.genai.expense.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.spring.genai.expense.Expense;
import com.spring.genai.expense.ExpenseRepository;
import com.spring.genai.expense.ExpenseStatus;
import com.spring.genai.expense.dto.AggregateView;
import com.spring.genai.expense.dto.ExpenseFilterRequest;
import com.spring.genai.users.AppUser;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Service
public class ExpenseQueryService {

	private final ExpenseRepository repository;

	@PersistenceContext
	private EntityManager entityManager;

	public ExpenseQueryService(ExpenseRepository repository) {
		this.repository = repository;
	}

	public Page<Expense> search(AppUser owner, ExpenseFilterRequest filter, Pageable pageable) {
		return repository.findAll(toSpecification(owner, filter), pageable);
	}

	public List<Expense> searchAll(AppUser owner, ExpenseFilterRequest filter) {
		return repository.findAll(toSpecification(owner, filter));
	}

	/** Total amount computed in SQL (SUM) instead of loading every matching row to sum in Java. */
	public BigDecimal sumAmount(AppUser owner, ExpenseFilterRequest filter) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<BigDecimal> query = cb.createQuery(BigDecimal.class);
		Root<Expense> root = query.from(Expense.class);
		query.select(cb.coalesce(cb.sum(root.get("amount")), BigDecimal.ZERO))
				.where(cb.and(buildPredicates(root, cb, owner, filter).toArray(new Predicate[0])));
		return entityManager.createQuery(query).getSingleResult();
	}

	public List<String> distinctCategories(AppUser owner) {
		return repository.findDistinctCategories(owner, ExpenseStatus.CONFIRMED);
	}

	/** Category/month totals computed in SQL (GROUP BY + SUM) instead of pulling every matching row into the JVM. */
	public AggregateView aggregates(AppUser owner, ExpenseFilterRequest filter) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();

		CriteriaQuery<Tuple> byCategoryQuery = cb.createTupleQuery();
		Root<Expense> byCategoryRoot = byCategoryQuery.from(Expense.class);
		byCategoryQuery.multiselect(byCategoryRoot.get("category"), cb.sum(byCategoryRoot.get("amount")))
				.where(cb.and(buildPredicates(byCategoryRoot, cb, owner, filter).toArray(new Predicate[0])))
				.groupBy(byCategoryRoot.get("category"));
		List<AggregateView.CategoryTotal> categoryTotals = entityManager.createQuery(byCategoryQuery).getResultList()
				.stream()
				.map(t -> new AggregateView.CategoryTotal(t.get(0, String.class), t.get(1, BigDecimal.class)))
				.toList();

		CriteriaQuery<Tuple> byMonthQuery = cb.createTupleQuery();
		Root<Expense> byMonthRoot = byMonthQuery.from(Expense.class);
		byMonthQuery
				.multiselect(byMonthRoot.get("expenseYear"), byMonthRoot.get("expenseMonth"),
						cb.sum(byMonthRoot.get("amount")))
				.where(cb.and(buildPredicates(byMonthRoot, cb, owner, filter).toArray(new Predicate[0])))
				.groupBy(byMonthRoot.get("expenseYear"), byMonthRoot.get("expenseMonth"))
				.orderBy(cb.asc(byMonthRoot.get("expenseYear")), cb.asc(byMonthRoot.get("expenseMonth")));
		List<AggregateView.MonthTotal> monthTotals = entityManager.createQuery(byMonthQuery).getResultList().stream()
				.map(t -> new AggregateView.MonthTotal(t.get(0, Integer.class), t.get(1, Integer.class),
						t.get(2, BigDecimal.class)))
				.toList();

		return new AggregateView(categoryTotals, monthTotals);
	}

	private Specification<Expense> toSpecification(AppUser owner, ExpenseFilterRequest filter) {
		return (root, query, cb) -> cb.and(buildPredicates(root, cb, owner, filter).toArray(new Predicate[0]));
	}

	private List<Predicate> buildPredicates(Root<Expense> root, CriteriaBuilder cb, AppUser owner,
			ExpenseFilterRequest filter) {
		List<Predicate> predicates = new ArrayList<>();
		predicates.add(cb.equal(root.get("owner"), owner));

		ExpenseStatus status = filter.status() != null ? filter.status() : ExpenseStatus.CONFIRMED;
		predicates.add(cb.equal(root.get("status"), status));

		if (filter.dateFrom() != null) {
			predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), filter.dateFrom()));
		}
		if (filter.dateTo() != null) {
			predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), filter.dateTo()));
		}
		if (filter.year() != null) {
			predicates.add(cb.equal(root.get("expenseYear"), filter.year()));
		}
		if (filter.month() != null) {
			predicates.add(cb.equal(root.get("expenseMonth"), filter.month()));
		}
		if (filter.categories() != null && !filter.categories().isEmpty()) {
			predicates.add(root.get("category").in(filter.categories()));
		}
		if (filter.amountMin() != null) {
			predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), filter.amountMin()));
		}
		if (filter.amountMax() != null) {
			predicates.add(cb.lessThanOrEqualTo(root.get("amount"), filter.amountMax()));
		}
		if (filter.sourceMode() != null) {
			predicates.add(cb.equal(root.get("sourceMode"), filter.sourceMode()));
		}
		if (filter.search() != null && !filter.search().isBlank()) {
			String like = "%" + filter.search().toLowerCase() + "%";
			predicates.add(cb.or(
					cb.like(cb.lower(root.get("description")), like),
					cb.like(cb.lower(root.get("category")), like)));
		}
		return predicates;
	}

}
