-- 虚构股权计划：授予、悬崖期 / 月度归属节点、行权申请
-- 全部数量均为整数股，NUMERIC(18,0)，应用层用 BigDecimal 计算。
-- 本系统不提供税务或投资建议。

create table grants (
    id             bigserial primary key,
    name           varchar(128) not null unique,
    grantee        varchar(128) not null default '',
    grant_date     date         not null,
    total_shares   numeric(18,0) not null check (total_shares > 0),
    cliff_months   integer      not null check (cliff_months >= 0),
    vesting_months integer      not null check (vesting_months > 0),
    check (cliff_months <= vesting_months),
    created_at     timestamptz  not null default now()
);

create table vesting_nodes (
    id              bigserial primary key,
    grant_id        bigint       not null references grants(id) on delete cascade,
    month_index     integer      not null,            -- 自授予日起的第 N 个自然月
    node_date       date         not null,            -- 归属日（月底授予自动落在各月最后一天）
    shares          numeric(18,0) not null check (shares >= 0),
    is_conditional  boolean      not null default false,
    condition_label varchar(256),
    condition_met   boolean      not null default false,
    satisfied_at    timestamptz,
    unique (grant_id, month_index)
);

create index idx_vesting_nodes_grant_date on vesting_nodes (grant_id, node_date);

create table exercise_requests (
    id            bigserial primary key,
    grant_id      bigint       not null references grants(id) on delete cascade,
    quantity      numeric(18,0) not null check (quantity > 0),
    status        varchar(16)  not null check (status in ('PENDING', 'CONFIRMED', 'CANCELLED')),
    requested_at  timestamptz  not null default now(),
    confirmed_at  timestamptz,
    cancelled_at  timestamptz,
    check (status <> 'CONFIRMED' or confirmed_at is not null),
    check (status <> 'CANCELLED' or cancelled_at is not null)
);

create index idx_exercise_requests_grant on exercise_requests (grant_id, requested_at);
