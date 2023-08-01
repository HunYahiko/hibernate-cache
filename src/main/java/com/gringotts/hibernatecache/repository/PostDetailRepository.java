package com.gringotts.hibernatecache.repository;

import com.gringotts.hibernatecache.domain.PostDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostDetailRepository extends JpaRepository<PostDetail, Long> {
}
