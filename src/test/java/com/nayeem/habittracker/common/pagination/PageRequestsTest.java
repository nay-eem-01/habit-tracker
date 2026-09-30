package com.nayeem.habittracker.common.pagination;

import com.nayeem.habittracker.common.exception.ApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageRequestsTest {

    private static final Set<String> SORTABLE = Set.of("createdAt", "name");

    @Test
    void clampsSizeAndPage() {
        assertThat(PageRequests.of(-3, 1000, "name", "asc", SORTABLE).getPageSize()).isEqualTo(100);
        assertThat(PageRequests.of(-3, 0, "name", "asc", SORTABLE).getPageSize()).isEqualTo(1);
        assertThat(PageRequests.of(-3, 20, "name", "asc", SORTABLE).getPageNumber()).isZero();
    }

    @Test
    void sortsByTheAllowedFieldThenIdForAStableOrder() {
        PageRequest request = PageRequests.of(0, 20, "name", "ASC", SORTABLE);
        assertThat(request.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "name").and(Sort.by("id")));
        assertThat(PageRequests.of(0, 20, "name", "anything", SORTABLE).getSort().getOrderFor("name").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void rejectsAFieldOutsideTheAllowlist() {
        assertThatThrownBy(() -> PageRequests.of(0, 20, "user.passwordHash", "asc", SORTABLE))
                .isInstanceOf(ApplicationException.class);
    }
}
