-- Profile pictures (roadmap 8.6): one small JPEG per user, kept in the database because the beta host's
-- disk is wiped on every deploy. Its own table so loading a user never loads the image.
-- users.avatar_updated_at tells the client whether there is one and when it changed (a cache key).
create table user_avatars (
    user_id bigint not null,
    content bytea  not null,
    constraint user_avatars_pkey primary key (user_id),
    constraint fk_user_avatars_user foreign key (user_id) references users (id) on delete cascade
);
alter table users add column avatar_updated_at timestamptz;
