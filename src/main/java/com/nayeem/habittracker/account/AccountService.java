package com.nayeem.habittracker.account;

import com.nayeem.habittracker.auth.AuthService;
import com.nayeem.habittracker.checkin.CheckInService;
import com.nayeem.habittracker.file.FileService;
import com.nayeem.habittracker.goal.GoalService;
import com.nayeem.habittracker.habit.HabitService;
import com.nayeem.habittracker.resource.ResourceService;
import com.nayeem.habittracker.user.UserResponse;
import com.nayeem.habittracker.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** The account as a whole, across every feature: exporting it, deleting it. */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserService userService;
    private final AuthService authService;
    private final FileService fileService;
    private final HabitService habitService;
    private final CheckInService checkInService;
    private final GoalService goalService;
    private final ResourceService resourceService;
    private final Clock clock;

    /** One read-only transaction, so the parts agree with each other. */
    @Transactional(readOnly = true)
    public AccountExport export(Long userId) {
        return new AccountExport(clock.instant(), UserResponse.from(userService.getById(userId)),
                habitService.exportAll(userId), checkInService.exportAll(userId), goalService.exportAll(userId),
                resourceService.exportAll(userId));
    }

    /** Everything goes: rows by cascade, file bytes after commit, sessions with their tokens. */
    @Transactional
    public void delete(Long userId, DeleteAccountRequest request) {
        authService.confirmPassword(userService.getById(userId), request.getPassword());
        fileService.deleteAllOfUserAfterCommit(userId);
        userService.delete(userId);
    }
}
