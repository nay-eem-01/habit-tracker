package com.nayeem.habittracker.common.pagination;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Builds a {@link PageRequest} from query parameters the api-conventions way: {@code size}
 * clamped to 1–{@value #MAX_SIZE}, {@code sortBy} checked against an allowlist (passing it straight
 * to JPA would let a caller sort by — and so probe — any column).
 */
public final class PageRequests {

    public static final String DEFAULT_PAGE = "0";
    public static final String DEFAULT_SIZE = "20";
    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    public static PageRequest of(int page, int size, String sortBy, String sortDir, Set<String> sortable) {
        if (!sortable.contains(sortBy)) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED,
                    "sortBy must be one of " + sortable.stream().sorted().toList());
        }
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        int clampedSize = Math.clamp(size, 1, MAX_SIZE);
        return PageRequest.of(Math.max(page, 0), clampedSize, Sort.by(direction, sortBy).and(Sort.by("id")));
    }

    /** For queries that bring their own ORDER BY: only {@code page} and {@code size}, clamped. */
    public static PageRequest unsorted(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_SIZE));
    }
}
