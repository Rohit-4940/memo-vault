package com.nexmemo.security.repository;

import com.nexmemo.core.entity.Menu;
import com.nexmemo.core.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MenuRepository extends JpaRepository<Menu, Long> {
    List<Menu> findByStatusOrderByOrdersAsc(Status status);
    List<Menu> findByParentIdOrderByOrdersAsc(Long parentId);
}
