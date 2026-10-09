package com.nayeem.habittracker.notification;

import com.nayeem.habittracker.checkin.HabitProgressService;
import com.nayeem.habittracker.habit.FrequencyConfig;
import com.nayeem.habittracker.habit.FrequencyType;
import com.nayeem.habittracker.habit.HabitRequest;
import com.nayeem.habittracker.habit.HabitService;
import com.nayeem.habittracker.push.PushService;
import com.nayeem.habittracker.support.IntegrationTest;
import com.nayeem.habittracker.user.User;
import com.nayeem.habittracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Not {@code @Transactional}: each habit runs in its own transaction, which has to really commit.
 * (The rows it leaves behind are harmless — each test uses its own minute.)
 */
class ReminderDeliveryIntegrationTest extends IntegrationTest {

    /** 07:30 on Tuesday 2031-01-07 in Dhaka. */
    private static final Instant DHAKA_0730 = Instant.parse("2031-01-07T01:30:00Z");

    @MockitoBean
    private NotificationSender sender;
    @MockitoSpyBean
    private HabitProgressService habitProgressService;
    @MockitoBean
    private PushService pushService;
    @Autowired
    private ReminderService reminderService;
    @Autowired
    private HabitService habitService;
    @Autowired
    private UserService userService;

    @Test
    void aNewReminderIsPushedOnceAndNeverEmailed() {
        User user = userService.createLocalUser("deliver@example.com", "not-a-real-hash", "Test", "Asia/Dhaka");
        HabitRequest request = new HabitRequest();
        request.setName("Read");
        request.setFrequencyType(FrequencyType.DAILY);
        request.setReminderTime(LocalTime.of(7, 30));
        habitService.create(user.getId(), request);

        assertEquals(1, reminderService.sendDue(DHAKA_0730));
        assertEquals(0, reminderService.sendDue(DHAKA_0730));   // same minute again: nothing new

        // pushed after commit, on another thread (PLAN.md §3.6); email is for account mail only
        verify(pushService, timeout(2000).times(1)).sendToUser(eq(user.getId()), eq("Reminder: Read"), any(), eq("/"));
        verify(sender, after(500).never()).send(any());
    }

    @Test
    void oneFailingHabitDoesNotStopTheOthers() {
        Instant now = Instant.parse("2032-01-06T02:15:00Z");   // 08:15 in Dhaka: no other test's minute
        User user = userService.createLocalUser("deliver.fail@example.com", "not-a-real-hash", "Test", "Asia/Dhaka");
        HabitRequest request = new HabitRequest();
        request.setName("Read");
        request.setFrequencyType(FrequencyType.X_TIMES_PER_WEEK);
        request.setFrequencyConfig(new FrequencyConfig(null, 3));
        request.setReminderTime(LocalTime.of(8, 15));
        Long broken = habitService.create(user.getId(), request).id();
        habitService.create(user.getId(), request);
        doThrow(new IllegalStateException("boom")).when(habitProgressService).doneDays(eq(broken), any(), any());

        assertEquals(1, reminderService.sendDue(now));
    }
}
