-- Promotional email only with consent (roadmap 8.2): off until the user opts in.
alter table users add column marketing_emails boolean not null default false;
