package com.nexmemo.web.service;

import com.nexmemo.core.entity.Menu;
import com.nexmemo.core.enums.Status;
import com.nexmemo.security.repository.MenuRepository;
import com.nexmemo.security.repository.RolePermissionRightsRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MenuService {

    private final MenuRepository menuRepository;
    private final RolePermissionRightsRepository rightsRepository;

    @Transactional(readOnly = true)
    public List<Menu> getAllMenus() {
        return menuRepository.findByStatusOrderByOrdersAsc(Status.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Menu getMenuById(Long id) {
        return menuRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Menu not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<Menu> getMenusByRoleId(Long roleId) {
        List<Long> menuIds = rightsRepository.findByRoleId(roleId)
                .stream().map(r -> r.getMenu().getId()).collect(Collectors.toList());
        return menuRepository.findAllById(menuIds).stream()
                .filter(m -> m.getStatus() == Status.ACTIVE)
                .sorted(Comparator.comparing(Menu::getOrders, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    public Menu createMenu(Menu menu) {
        menu = menuRepository.save(menu);
        log.info("Menu created: {}", menu.getMenuName());
        return menu;
    }

    public Menu updateMenu(Long id, Menu menu) {
        Menu existing = menuRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Menu not found with id: " + id));
        existing.setMenuName(menu.getMenuName());
        existing.setFaIcon(menu.getFaIcon());
        existing.setFrontUrl(menu.getFrontUrl());
        existing.setOrders(menu.getOrders());
        existing.setParentId(menu.getParentId());
        existing.setStatus(menu.getStatus());
        existing = menuRepository.save(existing);
        log.info("Menu updated: {}", existing.getMenuName());
        return existing;
    }

    public void deleteMenu(Long id) {
        if (!menuRepository.existsById(id)) {
            throw new EntityNotFoundException("Menu not found with id: " + id);
        }
        rightsRepository.findByRoleId(id).forEach(rm -> rightsRepository.deleteById(rm.getId()));
        menuRepository.deleteById(id);
        log.info("Menu deleted: {}", id);
    }
}
