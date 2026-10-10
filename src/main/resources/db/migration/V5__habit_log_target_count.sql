-- A day is done when its count reaches the target that applied when it was logged, so raising a
-- habit's target later doesn't re-judge (and un-do) the past. Existing rows take today's target.
alter table habit_logs add column target_count integer;
update habit_logs l set target_count = h.target_count from habits h where h.id = l.habit_id;
alter table habit_logs alter column target_count set not null;
