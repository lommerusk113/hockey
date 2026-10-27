-- Inactive players keep their history but are hidden when recording matches
alter table player add column active boolean not null default true;
