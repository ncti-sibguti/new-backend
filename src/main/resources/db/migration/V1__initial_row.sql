create table users
(
    id                         bigint generated by default as identity
        primary key,
    firstname                  varchar(255),
    lastname                   varchar(255),
    surname                    varchar(255),
    email                      varchar(255) UNIQUE,
    password                   varchar(255),
    is_account_non_expired     boolean,
    is_account_non_locked      boolean,
    is_credentials_non_expired boolean,
    is_enabled                 boolean
);

insert into users (firstname, lastname, surname, email, password, is_account_non_expired, is_account_non_locked,
                   is_credentials_non_expired, is_enabled)
values ('Админ', 'Админович', 'Администрейтович', 'admin@gmail.com',
        '$2a$10$xjDYCTaxIzZcr5jap1AU1Oe2VzwjasEH5ISvIBuL9QgYXb3W.Tmai', true, true, true, true);

create table role
(
    id          bigint generated by default as identity
        primary key,
    name        varchar(255),
    description varchar(255)
);

INSERT INTO role(name, description)
values ('ROLE_ADMIN', 'Админ'),
       ('ROLE_STUDENT', 'Студент'),
       ('ROLE_TEACHER', 'Преподаватель');

create table user_role
(
    user_id bigint not null,
    role_id bigint not null
);
insert into user_role (user_id, role_id)
values (1, 1);