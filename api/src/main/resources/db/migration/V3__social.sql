create table app_user (
    id             bigserial primary key,
    google_subject text        not null unique,
    email          text        not null,
    name           text        not null,
    picture_url    text,
    player_id      bigint unique references player (id) on delete set null,
    notify_matches boolean     not null default true,
    notify_chat    boolean     not null default true,
    created_at     timestamptz not null default now()
);

create table match_comment (
    id         bigserial primary key,
    match_id   bigint      not null references match (id) on delete cascade,
    user_id    bigint      not null references app_user (id),
    body       text        not null check (length(body) between 1 and 1000),
    created_at timestamptz not null default now()
);
create index match_comment_match_idx on match_comment (match_id);

create table match_reaction (
    id       bigserial primary key,
    match_id bigint not null references match (id) on delete cascade,
    user_id  bigint not null references app_user (id),
    emoji    text   not null,
    unique (match_id, user_id, emoji)
);

create table chat_message (
    id         bigserial primary key,
    user_id    bigint      not null references app_user (id),
    body       text        not null check (length(body) between 1 and 2000),
    created_at timestamptz not null default now()
);

create table push_subscription (
    id       bigserial primary key,
    user_id  bigint not null references app_user (id) on delete cascade,
    endpoint text   not null unique,
    p256dh   text   not null,
    auth     text   not null
);
