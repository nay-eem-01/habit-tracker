-- When a refresh token was rotated (used for a new pair). Reuse detection fires only for a rotated
-- token presented again — a token revoked by "sign out everywhere" (password reset or change) is
-- just invalid. Without this, another device's old cookie after a password change looked like
-- theft and signed the user out of the session they had just changed it in (found in 2.4b).
-- Tokens rotated before this migration have no timestamp: their reuse is treated as plain invalid.

alter table refresh_tokens add column rotated_at timestamp(6) with time zone;
