package com.nayeem.habittracker.configs;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JpaAuditingConfigTest {

    @Test
    void noAuthenticationIsSystem() {
        assertThat(JpaAuditingConfig.currentAuditor(null)).isEqualTo("SYSTEM");
    }

    @Test
    void anonymousIsSystem() {
        var anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        assertThat(JpaAuditingConfig.currentAuditor(anonymous)).isEqualTo("SYSTEM");
    }

    @Test
    void unauthenticatedTokenIsSystem() {
        var unauthenticated = UsernamePasswordAuthenticationToken.unauthenticated("a@b.com", "pw");
        assertThat(JpaAuditingConfig.currentAuditor(unauthenticated)).isEqualTo("SYSTEM");
    }

    @Test
    void signedInUserIsTheirName() {
        var user = UsernamePasswordAuthenticationToken.authenticated("a@b.com", null, List.of());
        assertThat(JpaAuditingConfig.currentAuditor(user)).isEqualTo("a@b.com");
    }
}
