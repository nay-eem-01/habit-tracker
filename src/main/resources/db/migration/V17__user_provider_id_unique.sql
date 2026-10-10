-- Google sign-in (roadmap 2.3) finds a user by Google's subject id first: one account per Google account.
create unique index uk_users_provider_id on users (provider_id) where provider_id is not null;
