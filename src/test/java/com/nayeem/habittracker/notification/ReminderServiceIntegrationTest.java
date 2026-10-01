package com.nayeem.habittracker.notification;

import com.nayeem.habittracker.checkin.CheckInRequest;
import com.nayeem.habittracker.checkin.CheckInService;
import com.nayeem.habittracker.habit.FrequencyConfig;
import com.nayeem.habittracker.habit.FrequencyType;
import com.nayeem.habittracker.habit.HabitRequest;
import com.nayeem.habittracker.habit.HabitResponse;
import com.nayeem.habittracker.habit.HabitService;
import com.nayeem.habittracker.support.IntegrationTest;
import com.nayeem.habittracker.user.User;
import com.nayeem.habittracker.user.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Transactional
class ReminderServiceIntegrationTest extends IntegrationTest {

    /** 07:30 on Tuesday 2030-01-01 in Dhaka (UTC+6). */
    private static final Instant DHAKA_0730 = Instant.parse("2030-01-01T01:30:00Z");

    @Autowired
    private ReminderService reminderService;
    @Autowired
    private HabitService habitService;
    @Autowired
    private CheckInService checkInService;
    @Autowired
    private UserService userService;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void firesAtTheOwnersLocalMinuteNotUtc() {
        User user = user("remind.local@example.com", "Asia/Dhaka");
        habit(user, "07:30", FrequencyType.DAILY, null);

        reminderService.sendDue(Instant.parse("2030-01-01T07:30:00Z")); // 13:30 in Dhaka
        reminderService.sendDue(DHAKA_0730.plus(1, ChronoUnit.MINUTES));
        reminderService.sendDue(DHAKA_0730.minus(1, ChronoUnit.MINUTES));
        assertEquals(0, unread(user));

        reminderService.sendDue(DHAKA_0730.plusSeconds(20)); // anywhere inside the minute
        assertEquals(1, unread(user));
    }

    @Test
    void theSameReminderIsNeverCreatedTwice() {
        User user = user("remind.twice@example.com", "Asia/Dhaka");
        habit(user, "07:30", FrequencyType.DAILY, null);

        reminderService.sendDue(DHAKA_0730);
        reminderService.sendDue(DHAKA_0730);
        assertEquals(1, unread(user));

        // the next day is a new reminder
        reminderService.sendDue(DHAKA_0730.plus(1, ChronoUnit.DAYS));
        assertEquals(2, unread(user));
    }

    @Test
    void skipsArchivedHabitsAndHabitsWithoutAReminder() {
        User user = user("remind.skip@example.com", "Asia/Dhaka");
        HabitResponse archived = habit(user, "07:30", FrequencyType.DAILY, null);
        habitService.setArchived(user.getId(), archived.id(), true);
        habit(user, null, FrequencyType.DAILY, null);
        entityManager.flush();

        reminderService.sendDue(DHAKA_0730);
        assertEquals(0, unread(user));
    }

    @Test
    void skipsAHabitAlreadyDoneToday() {
        User user = user("remind.done@example.com", "Asia/Dhaka");
        Instant now = Instant.now();
        String time = LocalTime.ofInstant(now, ZoneId.of("Asia/Dhaka")).truncatedTo(ChronoUnit.MINUTES).toString();
        HabitResponse done = habit(user, time, FrequencyType.DAILY, null);
        habit(user, time, FrequencyType.DAILY, null);
        checkInService.checkIn(user.getId(), done.id(), new CheckInRequest());
        entityManager.flush();

        reminderService.sendDue(now);
        assertEquals(1, unread(user)); // only the habit that is still open
    }

    @Test
    void specificDaysRemindOnlyOnTheirWeekdays() {
        User user = user("remind.days@example.com", "Asia/Dhaka");
        habit(user, "07:30", FrequencyType.SPECIFIC_DAYS, new FrequencyConfig(Set.of(DayOfWeek.MONDAY), null));
        HabitResponse tuesdays = habit(user, "07:30", FrequencyType.SPECIFIC_DAYS,
                new FrequencyConfig(Set.of(DayOfWeek.TUESDAY), null));

        reminderService.sendDue(DHAKA_0730); // a Tuesday in Dhaka
        assertEquals(1, unread(user));
        assertEquals(tuesdays.id(), notificationRepository.findForUser(user.getId(),
                org.springframework.data.domain.PageRequest.of(0, 5)).getContent().get(0).getHabit().getId());
    }

    @Test
    void timesPerWeekRemindsWhileTheQuotaIsOpen() {
        User user = user("remind.week@example.com", "Asia/Dhaka");
        habit(user, "07:30", FrequencyType.X_TIMES_PER_WEEK, new FrequencyConfig(null, 3));

        reminderService.sendDue(DHAKA_0730);
        assertEquals(1, unread(user));
    }

    private User user(String email, String timezone) {
        return userService.createLocalUser(email, "not-a-real-hash", "Test User", timezone);
    }

    private HabitResponse habit(User user, String reminderTime, FrequencyType type, FrequencyConfig config) {
        HabitRequest request = new HabitRequest();
        request.setName("Read");
        request.setFrequencyType(type);
        request.setFrequencyConfig(config);
        request.setReminderTime(reminderTime == null ? null : LocalTime.parse(reminderTime));
        HabitResponse habit = habitService.create(user.getId(), request);
        entityManager.flush();
        return habit;
    }

    private long unread(User user) {
        return notificationRepository.countByUserIdAndReadAtIsNull(user.getId());
    }
}
