package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.util.List;

@Entity
@Table(name = "role_permission_rights")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class RolePermissionRights extends BaseEntity<Long> implements Serializable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "role_permission_api",
            joinColumns = @JoinColumn(name = "rights_id"),
            inverseJoinColumns = @JoinColumn(name = "api_id"))
    private List<UrlApi> apiRights;
}
