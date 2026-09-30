package com.nayeem.habittracker.checkin;

import com.nayeem.habittracker.habit.FrequencyType;
import com.nayeem.habittracker.habit.HabitRequest;
import com.nayeem.habittracker.habit.HabitService;
import com.nayeem.habittracker.support.IntegrationTest;
import com.nayeem.habittracker.user.User;
import com.nayeem.habittracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Not @Transactional: each check-in must commit on its own thread for the race to be real. */
class CheckInConcurrencyIntegrationTest extends IntegrationTest {

    private static final int THREADS = 8;

    @Autowired
    private CheckInService checkInService;

    @Autowired
    private HabitService habitService;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void simultaneousCheckInsLeaveOneRow() throws Exception {
        User user = userService.createLocalUser("race-" + System.nanoTime() + "@example.com", "hash", "Race", null);
        HabitRequest request = new HabitRequest();
        request.setName("Race");
        request.setFrequencyType(FrequencyType.DAILY);
        Long habitId = habitService.create(user.getId(), request).id();

        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            for (int i = 0; i < THREADS; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return checkInService.checkIn(user.getId(), habitId, new CheckInRequest());
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(); // rethrows if any check-in failed
            }
        }

        Integer rows = jdbcTemplate.queryForObject("select count(*) from habit_logs where habit_id = ?",
                Integer.class, habitId);
        assertThat(rows).isEqualTo(1);
    }
}
