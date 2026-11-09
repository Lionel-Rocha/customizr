create type role as enum('PLAYER','MASTER');

create table users (
id uuid primary key not null,
name varchar(30) not null,
role role not null
);