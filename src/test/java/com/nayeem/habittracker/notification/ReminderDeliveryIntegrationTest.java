package com.nayeem.habittracker.notification;

import com.nayeem.habittracker.checkin.HabitProgressService;
import com.nayeem.habittracker.habit.FrequencyConfig;
import com.nayeem.habittracker.habit.FrequencyType;
import com.nayeem.habittracker.habit.HabitRequest;
import com.nayeem.habittracker.habit.HabitService;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Not {@code @Transactional}: the event is sent after commit, so the run has to really commit.
 * (The rows it leaves behind are harmless — the other tests only look at their own users.)
 */
class ReminderDeliveryIntegrationTest extends IntegrationTest {

    /** 07:30 on Tuesday 2031-01-07 in Dhaka. */
    private static final Instant DHAKA_0730 = Instant.parse("2031-01-07T01:30:00Z");

    @MockitoBean
    private NotificationSender sender;
    @MockitoSpyBean
    private HabitProgressService habitProgressService;
    @Autowired
    private ReminderService reminderService;
    @Autowired
    private HabitService habitService;
    @Autowired
    private UserService userService;

    @Test
    void aNewReminderIsSentOnceAfterItIsCommitted() {
        User user = userService.createLocalUser("deliver@example.com", "not-a-real-hash", "Test", "Asia/Dhaka");
        HabitRequest request = new HabitRequest();
        request.setName("Read");
        request.setFrequencyType(FrequencyType.DAILY);
        request.setReminderTime(LocalTime.of(7, 30));
        habitService.create(user.getId(), request);

        reminderService.sendDue(DHAKA_0730);
        reminderService.sendDue(DHAKA_0730); // same minute again: already there, nothing more to send

        verify(sender, times(1)).send(argThat(n -> n.userId().equals(user.getId())
                && n.title().equals("Reminder: Read") && n.email().equals("deliver@example.com")));
        verify(sender, never()).send(argThat(n -> !n.userId().equals(user.getId())));
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
        verify(sender, times(1)).send(argThat(n -> n.userId().equals(user.getId())));
    }
}
