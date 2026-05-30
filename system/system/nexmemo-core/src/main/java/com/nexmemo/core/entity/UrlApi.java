package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

@Entity
@Table(name = "url_api")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class UrlApi extends BaseEntity<Long> implements Serializable {

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String method = "GET";

    private String description;
}
