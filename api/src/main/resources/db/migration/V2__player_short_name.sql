alter table player add column short_name text;
update player set short_name = name;
alter table player alter column short_name set not null;
create unique index player_short_name_key on player (lower(short_name));
create unique index player_name_lower_key on player (lower(name));
