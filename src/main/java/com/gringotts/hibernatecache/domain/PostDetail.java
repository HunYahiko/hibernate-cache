package com.gringotts.hibernatecache.domain;

import lombok.Data;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity(name = "POST_DETAILS")
@Table(name = "POST_DETAILS")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Data
public class PostDetail {

    @Id
    private Long id;

    @Column(name = "created_on")
    private LocalDateTime createdOn;

    @Column(name = "created_by")
    private String createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId
    private Post post;
}
