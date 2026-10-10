-- Rest days (roadmap 9.3): a day the user chose to rest a habit — it neither breaks nor extends the
-- streak and isn't expected in completion rates. Stored on the day's log row (one row per habit and day).
alter table habit_logs add column rest boolean not null default false;
