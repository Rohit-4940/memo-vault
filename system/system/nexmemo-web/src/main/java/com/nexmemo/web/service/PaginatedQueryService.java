package com.nexmemo.web.service;

import com.nexmemo.core.dto.PageResponse;
import com.nexmemo.core.dto.PaginatedRequest;
import com.nexmemo.core.specification.GenericSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaginatedQueryService {

    public <T> PageResponse<T> query(JpaSpecificationExecutor<T> repo, PaginatedRequest req, List<String> searchFields) {
        Specification<T> spec = GenericSpecification.withFilters(req.getFilters(), req.getSearch(), searchFields);
        Sort sort = req.getSortDir().equalsIgnoreCase("asc")
                ? Sort.by(req.getSortBy()).ascending()
                : Sort.by(req.getSortBy()).descending();
        Pageable pageable = PageRequest.of(req.getPage(), req.getSize(), sort);
        Page<T> page = repo.findAll(spec, pageable);
        return PageResponse.from(page);
    }
}
