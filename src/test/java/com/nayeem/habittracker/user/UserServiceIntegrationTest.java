package com.nayeem.habittracker.user;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import com.nayeem.habittracker.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class UserServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private UserService userService;

    @Test
    void createsLocalUserWithNormalizedEmailAndAuditColumns() {
        User user = userService.createLocalUser("  Nayeem@Example.COM ", "hash", " Nayeem ", "Asia/Dhaka");

        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("nayeem@example.com");
        assertThat(user.getName()).isEqualTo("Nayeem");
        assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(user.getTimezone()).isEqualTo("Asia/Dhaka");
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getCreatedBy()).isEqualTo("SYSTEM");
    }

    @Test
    void blankTimezoneDefaultsToUtc() {
        User user = userService.createLocalUser("utc@example.com", "hash", "Utc", " ");
        assertThat(user.getTimezone()).isEqualTo("UTC");
    }

    @Test
    void unknownTimezoneIsRejected() {
        assertThatThrownBy(() -> userService.createLocalUser("tz@example.com", "hash", "Tz", "Mars/Olympus"))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_INVALID_TIMEZONE);
    }

    @Test
    void sameEmailInAnotherCaseIsTaken() {
        userService.createLocalUser("dup@example.com", "hash", "First", null);

        assertThatThrownBy(() -> userService.createLocalUser("DUP@example.com", "hash", "Second", null))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_EMAIL_TAKEN);
    }

    @Test
    void findsByEmailInAnyCase() {
        userService.createLocalUser("find@example.com", "hash", "Find", null);
        assertThat(userService.findByEmail("FIND@Example.com")).isPresent();
    }

    @Test
    void unknownIdIsNotFound() {
        assertThatThrownBy(() -> userService.getById(-1L))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
