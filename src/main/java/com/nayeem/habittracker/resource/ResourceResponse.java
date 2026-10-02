package com.nayeem.habittracker.resource;

import java.time.Instant;

public record ResourceResponse(
        Long id,
        ResourceType type,
        String title,
        String body,
        String url,
        Long goalId,
        boolean pinned,
        Instant createdAt) {

    static ResourceResponse from(Resource resource) {
        return new ResourceResponse(resource.getId(), resource.getType(), resource.getTitle(), resource.getBody(),
                resource.getUrl(), resource.getGoal() == null ? null : resource.getGoal().getId(),
                resource.isPinned(), resource.getCreatedAt());
    }
}
