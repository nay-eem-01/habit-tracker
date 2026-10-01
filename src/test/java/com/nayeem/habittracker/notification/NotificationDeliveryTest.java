package com.nayeem.habittracker.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentCaptor.forClass;

class NotificationDeliveryTest {

    private static final OutgoingNotification MESSAGE =
            new OutgoingNotification(7L, "reader@example.com", "Reminder: Read", "Time to do it.");

    @Test
    void emailGoesToTheUserFromTheConfiguredAddress() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        NotificationProperties properties = new NotificationProperties();
        properties.getEmail().setFrom("habits@example.com");

        new EmailNotificationSender(mailSender, properties).send(MESSAGE);

        var captor = forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertEquals("habits@example.com", sent.getFrom());
        assertEquals("reader@example.com", sent.getTo()[0]);
        assertEquals("Reminder: Read", sent.getSubject());
        assertEquals("Time to do it.", sent.getText());
    }

    @Test
    void aFailingChannelDoesNotPropagate() {
        NotificationSender sender = mock(NotificationSender.class);
        doThrow(new MailSendException("smtp down for reader@example.com")).when(sender).send(any());

        assertDoesNotThrow(() -> new NotificationDispatcher(sender).onCommitted(MESSAGE));
        verify(sender).send(MESSAGE);
    }

    @Test
    void theAddressStaysOutOfToString() {
        assertFalse(MESSAGE.toString().contains("reader@example.com"));
    }

    @Test
    void emailEnabledWithoutAMailServerRefusesToStart() {
        new ApplicationContextRunner()
                .withUserConfiguration(NotificationProperties.class, EmailNotificationSender.class)
                .withPropertyValues("app.notifications.email.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void emailDisabledFallsBackToTheConsoleChannel() {
        new ApplicationContextRunner()
                .withUserConfiguration(NotificationProperties.class, EmailNotificationSender.class,
                        ConsoleNotificationSender.class)
                .run(context -> assertThat(context).hasSingleBean(ConsoleNotificationSender.class)
                        .doesNotHaveBean(EmailNotificationSender.class));
    }
}
