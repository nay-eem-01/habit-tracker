-- Plans (roadmap 9.4): FREE has limits, PRO none. Everyone starts on FREE; there is no billing yet.
alter table users add column plan varchar(10) not null default 'FREE';
alter table users add constraint users_plan_check check (plan in ('FREE', 'PRO'));
