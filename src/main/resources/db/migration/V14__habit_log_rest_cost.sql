-- Rest days past the free one cost XP (roadmap 9.3d): what this one cost, refunded when it ends.
alter table habit_logs add column rest_cost_xp integer not null default 0;
