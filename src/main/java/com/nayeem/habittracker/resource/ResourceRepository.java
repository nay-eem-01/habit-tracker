package com.nayeem.habittracker.resource;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * Every query is scoped by the owner (security-checklist): a resource that isn't yours is simply
 * not found. Package-private — other features go through the resource service.
 */
interface ResourceRepository extends JpaRepository<Resource, Long>, JpaSpecificationExecutor<Resource> {

    Optional<Resource> findByIdAndUserId(Long id, Long userId);
}
