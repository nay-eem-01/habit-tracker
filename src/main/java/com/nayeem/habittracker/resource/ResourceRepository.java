package com.nayeem.habittracker.resource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * Every query is scoped by the owner (security-checklist): a resource that isn't yours is simply
 * not found. Package-private — other features go through the resource service.
 */
interface ResourceRepository extends JpaRepository<Resource, Long>, JpaSpecificationExecutor<Resource> {

    Optional<Resource> findByIdAndUserId(Long id, Long userId);

    /** Lists fetch the file with each row — one query per page, not one more per file. */
    @Override
    @EntityGraph(attributePaths = "file")
    Page<Resource> findAll(Specification<Resource> spec, Pageable pageable);
}
