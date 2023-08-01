package com.gringotts.hibernatecache.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity(name = "POST_COMMENTS")
@Table(name = "POST_COMMENTS")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Data
@NoArgsConstructor
public class PostComment {

    @Id
    private Long id;

    @ManyToOne
    private Post post;

    private String comment;

    public PostComment(Long id,
                       String comment) {
        this.id = id;
        this.comment = comment;
    }
}
