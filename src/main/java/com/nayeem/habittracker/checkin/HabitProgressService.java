package com.nayeem.habittracker.checkin;

import com.nayeem.habittracker.habit.Habit;
import com.nayeem.habittracker.habit.HabitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;

/** Read-side numbers computed from a habit's logs — never stored (plan §1.3). */
@Service
@RequiredArgsConstructor
public class HabitProgressService {

    private final HabitService habitService;
    private final HabitLogRepository habitLogRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Streak streak(Long userId, Long habitId) {
        Habit habit = habitService.getOwnedHabit(userId, habitId);
        ZoneId zone = ZoneId.of(habit.getUser().getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate start = habit.getCreatedAt().atZone(zone).toLocalDate();
        var doneDays = new HashSet<>(habitLogRepository.findDoneDays(habit.getId()));
        return StreakCalculator.calculate(habit.getFrequencyType(), habit.getFrequencyConfig(), doneDays, start, today);
    }
}
