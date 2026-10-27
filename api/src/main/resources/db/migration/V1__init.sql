create table player (
    id   bigserial primary key,
    name text not null unique
);

create table match (
    id             bigserial primary key,
    played_at      timestamptz not null default now(),
    home_player_id bigint      not null references player (id),
    away_player_id bigint      not null references player (id),
    home_goals     int         not null check (home_goals >= 0),
    away_goals     int         not null check (away_goals >= 0),
    check (home_player_id <> away_player_id)
);
