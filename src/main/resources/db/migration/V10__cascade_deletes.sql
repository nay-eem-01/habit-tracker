-- Deleting an account removes everything of the user (roadmap 8.3); deleting a habit removes its logs
-- and reminders (8.5). The database does it, so no table can be forgotten.
alter table refresh_tokens drop constraint fk1lih5y2npsf8u5o3vhdb9y0os,
    add constraint fk_refresh_tokens_user foreign key (user_id) references users (id) on delete cascade;
alter table one_time_tokens drop constraint fk_password_reset_tokens_user,
    add constraint fk_one_time_tokens_user foreign key (user_id) references users (id) on delete cascade;
alter table goals drop constraint fkb1mp6ulyqkpcw6bc1a2mr7v1g,
    add constraint fk_goals_user foreign key (user_id) references users (id) on delete cascade;
alter table habits drop constraint fkg3n2qqwmsyv3517xdcosouk9i,
    add constraint fk_habits_user foreign key (user_id) references users (id) on delete cascade;
alter table habit_logs drop constraint fksoqj2dqbm162ouaydnbwb21uj,
    add constraint fk_habit_logs_habit foreign key (habit_id) references habits (id) on delete cascade;
alter table notifications drop constraint fk9y21adhxn0ayjhfocscqox7bh,
    add constraint fk_notifications_user foreign key (user_id) references users (id) on delete cascade;
alter table notifications drop constraint fkechmhnlewtjn0m7jjmy8jilqb,
    add constraint fk_notifications_habit foreign key (habit_id) references habits (id) on delete cascade;
alter table stored_files drop constraint fkc0pxhjsng9h58ifnju7u0hol3,
    add constraint fk_stored_files_user foreign key (user_id) references users (id) on delete cascade;
alter table resources drop constraint fkcoba1blh4w96p6n34i4xfoiyp,
    add constraint fk_resources_user foreign key (user_id) references users (id) on delete cascade;
