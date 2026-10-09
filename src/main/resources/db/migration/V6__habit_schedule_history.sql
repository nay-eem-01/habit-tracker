-- Changing a habit's schedule starts a new streak from that day; the schedules before it are kept so
-- the XP earned under them stays (PLAN.md §3.3).
alter table habits add column schedule_since date;
alter table habits add column past_schedules jsonb not null default '[]';
