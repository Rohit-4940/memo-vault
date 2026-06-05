package com.nexmemo.core.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GenericSpecification {

    private GenericSpecification() {}

    public static <T> Specification<T> withFilters(Map<String, String> exactFilters, String searchQuery, List<String> searchFields) {
        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (exactFilters != null) {
                for (Map.Entry<String, String> entry : exactFilters.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                        predicates.add(cb.equal(resolvePath(root, entry.getKey()), entry.getValue()));
                    }
                }
            }

            if (searchQuery != null && !searchQuery.trim().isEmpty() && searchFields != null && !searchFields.isEmpty()) {
                List<Predicate> searchPredicates = new ArrayList<>();
                String pattern = "%" + searchQuery.trim().toLowerCase() + "%";
                for (String field : searchFields) {
                    if (field != null) {
                        searchPredicates.add(cb.like(cb.lower(resolvePath(root, field).as(String.class)), pattern));
                    }
                }
                if (!searchPredicates.isEmpty()) {
                    predicates.add(cb.or(searchPredicates.toArray(new Predicate[0])));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static <T> Specification<T> withFilterCriteria(Map<String, FilterCriteria> filters) {
        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filters == null) return cb.and();

            for (FilterCriteria fc : filters.values()) {
                if (fc == null || fc.getField() == null) continue;
                Path<?> path = resolvePath(root, fc.getField());

                switch (fc.getOperator()) {
                    case EQ:
                        if (fc.getValue() != null) predicates.add(cb.equal(path, fc.getValue()));
                        break;
                    case NEQ:
                        if (fc.getValue() != null) predicates.add(cb.notEqual(path, fc.getValue()));
                        break;
                    case LIKE:
                        if (fc.getValue() != null && !fc.getValue().isEmpty())
                            predicates.add(cb.like(cb.lower(path.as(String.class)), "%" + fc.getValue().toLowerCase() + "%"));
                        break;
                    case GT:
                        if (fc.getValue() != null)
                            predicates.add(cb.greaterThan(path.as(Comparable.class), (Comparable) fc.getValue()));
                        break;
                    case LT:
                        if (fc.getValue() != null)
                            predicates.add(cb.lessThan(path.as(Comparable.class), (Comparable) fc.getValue()));
                        break;
                    case GTE:
                        if (fc.getValue() != null)
                            predicates.add(cb.greaterThanOrEqualTo(path.as(Comparable.class), (Comparable) fc.getValue()));
                        break;
                    case LTE:
                        if (fc.getValue() != null)
                            predicates.add(cb.lessThanOrEqualTo(path.as(Comparable.class), (Comparable) fc.getValue()));
                        break;
                    case IN:
                        if (fc.getValues() != null && !fc.getValues().isEmpty()) {
                            CriteriaBuilder.In<Object> in = cb.in(path);
                            for (String v : fc.getValues()) in.value(v);
                            predicates.add(in);
                        } else if (fc.getValue() != null) {
                            predicates.add(cb.equal(path, fc.getValue()));
                        }
                        break;
                    case BETWEEN:
                        if (fc.getValues() != null && fc.getValues().size() >= 2) {
                            predicates.add(cb.between(path.as(Comparable.class), (Comparable) fc.getValues().get(0), (Comparable) fc.getValues().get(1)));
                        }
                        break;
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Path<?> resolvePath(Root<?> root, String field) {
        String[] parts = field.split("\\.");
        Path<?> path = root;
        for (String part : parts) {
            path = path.get(part);
        }
        return path;
    }
}
