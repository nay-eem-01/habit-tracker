-- Admins (roadmap 12.1): everyone is a USER; an admin is made by hand:
--   update users set role = 'ADMIN' where email = '...';
-- A suspended user (suspended_at set) can't sign in and their tokens stop working.
alter table users add column role varchar(10) not null default 'USER';
alter table users add constraint users_role_check check (role in ('USER', 'ADMIN'));
alter table users add column suspended_at timestamptz;
