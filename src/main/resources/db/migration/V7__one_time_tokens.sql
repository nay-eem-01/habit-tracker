-- Reset links become one kind of one-time token; email verification links are the other (roadmap 8.1).
alter table password_reset_tokens rename to one_time_tokens;
alter table one_time_tokens add column purpose varchar(30) not null default 'PASSWORD_RESET';
alter table one_time_tokens alter column purpose drop default;
alter table one_time_tokens add constraint one_time_tokens_purpose_check
    check (purpose in ('PASSWORD_RESET', 'EMAIL_VERIFICATION'));
