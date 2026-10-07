package com.nayeem.habittracker.support;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Attribute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every enum column's check constraint, as the migrations build it, allows exactly the enum's
 * values. Adding an enum value without a migration fails here instead of in production with a 500
 * (as FILE did on a pre-R.3 database, 2026-10-07).
 */
class EnumCheckConstraintsTest extends IntegrationTest {

    /** "Entity.attribute" → the check constraint guarding its column. */
    private static final Map<String, String> CONSTRAINTS = Map.of(
            "User.authProvider", "users_auth_provider_check",
            "Goal.status", "goals_status_check",
            "Habit.frequencyType", "habits_frequency_type_check",
            "Notification.type", "notifications_type_check",
            "Resource.type", "resources_type_check");

    private static final Pattern QUOTED = Pattern.compile("'([^']+)'");

    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void everyEnumColumnIsListed() {
        Set<String> enumAttributes = entityManagerFactory.getMetamodel().getEntities().stream()
                .flatMap(entity -> entity.getAttributes().stream()
                        .filter(attribute -> attribute.getJavaType().isEnum())
                        .map(attribute -> entity.getName() + "." + attribute.getName()))
                .collect(Collectors.toCollection(TreeSet::new));

        // a new enum column needs its constraint added above (and in a migration)
        assertThat(enumAttributes).isEqualTo(new TreeSet<>(CONSTRAINTS.keySet()));
    }

    @Test
    void eachCheckAllowsExactlyTheEnumsValues() {
        entityManagerFactory.getMetamodel().getEntities().forEach(entity -> entity.getAttributes().stream()
                .filter(attribute -> attribute.getJavaType().isEnum())
                .forEach(attribute -> {
                    String constraint = CONSTRAINTS.get(entity.getName() + "." + attribute.getName());
                    assertThat(allowedValues(constraint))
                            .as("%s (%s.%s)", constraint, entity.getName(), attribute.getName())
                            .isEqualTo(enumValues(attribute));
                }));
    }

    private Set<String> allowedValues(String constraint) {
        String definition = jdbcTemplate.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint where conname = ?", String.class, constraint);
        Set<String> values = new TreeSet<>();
        Matcher matcher = QUOTED.matcher(definition);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private static Set<String> enumValues(Attribute<?, ?> attribute) {
        return Arrays.stream(attribute.getJavaType().getEnumConstants())
                .map(value -> ((Enum<?>) value).name())
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
