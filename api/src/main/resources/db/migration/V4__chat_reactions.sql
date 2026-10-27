create table chat_reaction (
    id         bigserial primary key,
    message_id bigint not null references chat_message (id) on delete cascade,
    user_id    bigint not null references app_user (id),
    emoji      text   not null,
    unique (message_id, user_id, emoji)
);
