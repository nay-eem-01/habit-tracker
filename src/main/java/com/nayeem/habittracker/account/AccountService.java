package com.nayeem.habittracker.account;

import com.nayeem.habittracker.auth.AuthService;
import com.nayeem.habittracker.file.FileService;
import com.nayeem.habittracker.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The account as a whole: deleting it (and, next, exporting it) across every feature. */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserService userService;
    private final AuthService authService;
    private final FileService fileService;

    /** Everything goes: rows by cascade, file bytes after commit, sessions with their tokens. */
    @Transactional
    public void delete(Long userId, DeleteAccountRequest request) {
        authService.confirmPassword(userService.getById(userId), request.getPassword());
        fileService.deleteAllOfUserAfterCommit(userId);
        userService.delete(userId);
    }
}
