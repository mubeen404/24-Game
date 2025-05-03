-- create_tables.sql

-- 1) Create database
CREATE DATABASE IF NOT EXISTS game24db;
USE game24db;

-- 2) User authentication table
CREATE TABLE IF NOT EXISTS UserInfo (
  username VARCHAR(255) PRIMARY KEY,
  password VARCHAR(255) NOT NULL
);

-- 3) Online user tracking
CREATE TABLE IF NOT EXISTS OnlineUser (
  username VARCHAR(255) PRIMARY KEY
);

-- 4) Game stats persistence (played/won and total time)
CREATE TABLE IF NOT EXISTS user_stats (
  username VARCHAR(255) PRIMARY KEY,
  games_played INT NOT NULL DEFAULT 0,
  games_won INT NOT NULL DEFAULT 0,
  total_time BIGINT NOT NULL DEFAULT 0
); 