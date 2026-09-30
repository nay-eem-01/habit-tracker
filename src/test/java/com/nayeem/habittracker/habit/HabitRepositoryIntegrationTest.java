package com.nayeem.habittracker.habit;

import com.nayeem.habittracker.support.IntegrationTest;
import com.nayeem.habittracker.user.User;
import com.nayeem.habittracker.user.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.THURSDAY;
import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class HabitRepositoryIntegrationTest extends IntegrationTest {

    @Autowired
    private HabitRepository repository;

    @Autowired
    private UserService userService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void frequencyConfigRoundTripsThroughJsonb() {
        User user = userService.createLocalUser("json@example.com", "hash", "Json", null);
        Habit habit = habit(user, "Gym", FrequencyType.SPECIFIC_DAYS, new FrequencyConfig(Set.of(MONDAY, THURSDAY), null));
        Habit weekly = habit(user, "Swim", FrequencyType.X_TIMES_PER_WEEK, new FrequencyConfig(null, 3));
        entityManager.flush();
        entityManager.clear();

        Habit loaded = repository.findById(habit.getId()).orElseThrow();
        assertThat(loaded.getFrequencyType()).isEqualTo(FrequencyType.SPECIFIC_DAYS);
        assertThat(loaded.getFrequencyConfig().days()).containsExactlyInAnyOrder(MONDAY, THURSDAY);
        assertThat(repository.findById(weekly.getId()).orElseThrow().getFrequencyConfig().timesPerWeek()).isEqualTo(3);

        Object column = entityManager.createNativeQuery(
                "select pg_typeof(frequency_config)::text from habits where id = :id")
                .setParameter("id", habit.getId()).getSingleResult();
        assertThat(column).isEqualTo("jsonb");
    }

    @Test
    void queriesAreScopedToTheOwner() {
        User alice = userService.createLocalUser("alice.h@example.com", "hash", "Alice", null);
        User bob = userService.createLocalUser("bob.h@example.com", "hash", "Bob", null);
        Habit alices = habit(alice, "Read", FrequencyType.DAILY, null);
        Habit archived = habit(alice, "Old", FrequencyType.DAILY, null);
        archived.setArchived(true);

        assertThat(repository.findByIdAndUserId(alices.getId(), alice.getId())).isPresent();
        assertThat(repository.findByIdAndUserId(alices.getId(), bob.getId())).isEmpty();
        assertThat(repository.findAllByUserIdAndArchived(alice.getId(), false, PageRequest.of(0, 10)))
                .extracting(Habit::getName).containsExactly("Read");
        assertThat(repository.findAllByUserIdAndArchived(bob.getId(), false, PageRequest.of(0, 10))).isEmpty();
    }

    private Habit habit(User user, String name, FrequencyType type, FrequencyConfig config) {
        Habit habit = new Habit();
        habit.setUser(user);
        habit.setName(name);
        habit.schedule(type, config);
        return repository.save(habit);
    }
}
