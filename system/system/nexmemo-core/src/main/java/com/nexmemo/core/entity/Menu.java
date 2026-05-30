package com.nexmemo.core.entity;

import com.nexmemo.core.enums.Status;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.io.Serializable;
import java.util.List;

@Entity
@Table(name = "menu")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Menu extends BaseEntity<Long> implements Serializable {

    @NotBlank
    @Column(name = "menu_name", nullable = false)
    private String menuName;

    @Column(name = "fa_icon")
    private String faIcon;

    @Column(name = "front_url")
    private String frontUrl;

    @Column(name = "orders")
    private Long orders;

    @Column(name = "parent_id")
    private Long parentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private List<Menu> subNavs;
}
