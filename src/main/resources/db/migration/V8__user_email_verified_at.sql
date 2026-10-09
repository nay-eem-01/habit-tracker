-- Email verification (roadmap 8.1b): when the user proved they own the address; null until then.
alter table users add column email_verified_at timestamp(6) with time zone;
