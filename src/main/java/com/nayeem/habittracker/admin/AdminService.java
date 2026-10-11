package com.nayeem.habittracker.admin;

import com.nayeem.habittracker.account.AccountService;
import com.nayeem.habittracker.auth.AuthService;
import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import com.nayeem.habittracker.common.response.PageResponse;
import com.nayeem.habittracker.user.Plan;
import com.nayeem.habittracker.user.Role;
import com.nayeem.habittracker.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What an admin can do to accounts. Every change is logged with the admin's id (the audit trail). */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserService userService;
    private final AuthService authService;
    private final AccountService accountService;
    private final AdminQueries queries;

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> users(String query, Pageable pageable) {
        return PageResponse.from(userService.search(query, pageable), AdminUserResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminUserDetail user(Long userId) {
        return queries.detail(AdminUserResponse.from(userService.getById(userId)));
    }

    @Transactional
    public AdminUserResponse changePlan(Long adminId, Long userId, Plan plan) {
        AdminUserResponse user = AdminUserResponse.from(userService.setPlan(userId, plan));
        log.info("Admin {} set the plan of user {} to {}", adminId, userId, plan);
        return user;
    }

    /** The user can't sign in, their access tokens stop working and their sessions end. */
    @Transactional
    public AdminUserResponse suspend(Long adminId, Long userId) {
        checkNotAdmin(userId);
        AdminUserResponse user = AdminUserResponse.from(userService.setSuspended(userId, true));
        int revoked = authService.signOutEverywhere(userId);
        log.info("Admin {} suspended user {}; {} session(s) signed out", adminId, userId, revoked);
        return user;
    }

    @Transactional
    public AdminUserResponse unsuspend(Long adminId, Long userId) {
        AdminUserResponse user = AdminUserResponse.from(userService.setSuspended(userId, false));
        log.info("Admin {} lifted the suspension of user {}", adminId, userId);
        return user;
    }

    /** Same as the user deleting their own account, without their password. */
    @Transactional
    public void delete(Long adminId, Long userId) {
        checkNotAdmin(userId);
        accountService.deleteEverything(userId);
        log.info("Admin {} deleted user {}", adminId, userId);
    }

    /** An admin is removed from the role in the database first; this also stops an admin locking themselves out. */
    private void checkNotAdmin(Long userId) {
        if (userService.getById(userId).getRole() == Role.ADMIN) {
            throw new ApplicationException(ErrorCode.ADMIN_TARGET_IS_ADMIN);
        }
    }
}
