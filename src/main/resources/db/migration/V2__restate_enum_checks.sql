-- Databases created by Hibernate before Flyway (baselined at version 1) can hold enum check
-- constraints from before a value was added: ddl-auto=update never changes an existing constraint.
-- That's how a database from before R.3 refused FILE resources (found 2026-10-07). Re-state every
-- enum check from the code; on a database built by V1 this changes nothing.
-- From now on, adding an enum value means a migration like this one (EnumCheckConstraintsTest fails
-- until it exists).

alter table users drop constraint if exists users_auth_provider_check,
    add constraint users_auth_provider_check check (auth_provider in ('LOCAL', 'GOOGLE'));

alter table goals drop constraint if exists goals_status_check,
    add constraint goals_status_check check (status in ('ACTIVE', 'ACHIEVED', 'ABANDONED'));

alter table habits drop constraint if exists habits_frequency_type_check,
    add constraint habits_frequency_type_check check (frequency_type in ('DAILY', 'SPECIFIC_DAYS', 'X_TIMES_PER_WEEK'));

alter table notifications drop constraint if exists notifications_type_check,
    add constraint notifications_type_check check (type in ('HABIT_REMINDER'));

alter table resources drop constraint if exists resources_type_check,
    add constraint resources_type_check check (type in ('NOTE', 'LINK', 'FILE'));
