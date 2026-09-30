package com.nayeem.habittracker.common.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * The page shape inside {@link HttpResponse#payload()} for every list endpoint. Our own record
 * rather than Spring's {@code Page}, whose JSON form Spring Data doesn't promise to keep stable.
 */
public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int number, int size) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }
}
