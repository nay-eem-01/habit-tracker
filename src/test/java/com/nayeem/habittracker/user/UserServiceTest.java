package com.nayeem.habittracker.user;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The unit-level checks that need no database; the rest are in {@link UserServiceIntegrationTest}. */
class UserServiceTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final UserService service = new UserService(repository);

    private static DataIntegrityViolationException violation(String sqlState) {
        return new DataIntegrityViolationException("constraint", new SQLException("failed", sqlState));
    }

    @Test
    void aUniqueViolationOnSaveIsReportedAsEmailTaken() {
        when(repository.saveAndFlush(any())).thenThrow(violation("23505"));

        assertThatThrownBy(() -> service.createLocalUser("a@example.com", "hash", "A", null))
                .isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_EMAIL_TAKEN));
    }

    @Test
    void anyOtherIntegrityViolationIsNotDisguisedAsEmailTaken() {
        DataIntegrityViolationException notNull = violation("23502");
        when(repository.saveAndFlush(any())).thenThrow(notNull);

        assertThatThrownBy(() -> service.createLocalUser("a@example.com", "hash", "A", null)).isSameAs(notNull);
    }

    @Test
    void findsTheUniqueViolationNestedInTheCauseChain() {
        var wrapped = new DataIntegrityViolationException("outer", new RuntimeException(new SQLException("x", "23505")));

        assertThat(UserService.isUniqueViolation(wrapped)).isTrue();
        assertThat(UserService.isUniqueViolation(new RuntimeException("no sql here"))).isFalse();
    }
}
