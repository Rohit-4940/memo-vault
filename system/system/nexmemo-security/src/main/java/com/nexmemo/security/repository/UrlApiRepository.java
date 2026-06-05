package com.nexmemo.security.repository;

import com.nexmemo.core.entity.UrlApi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface UrlApiRepository extends JpaRepository<UrlApi, Long>, JpaSpecificationExecutor<UrlApi> {
}
