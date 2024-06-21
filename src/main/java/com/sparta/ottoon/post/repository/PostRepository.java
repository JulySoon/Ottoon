package com.sparta.ottoon.post.repository;

import com.sparta.ottoon.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {
}