-- Quit habits (roadmap 9.2): a check-in is a slip, a clean day is one without.
alter table habits add column kind varchar(10) not null default 'BUILD';
alter table habits add constraint habits_kind_check check (kind in ('BUILD', 'QUIT'));
