create type role as enum('PLAYER','MASTER');

create table users (
id uuid primary key not null,
name varchar(30) not null,
role role not null
);

create table campaign (
    id uuid not null,
    name varchar(255) not null,
    master_id uuid not null,
    code varchar(8) not null,
    created_at timestamp(6) not null,
    primary key (id),
    constraint fk_campaign_master foreign key (master_id) references users (id)
);