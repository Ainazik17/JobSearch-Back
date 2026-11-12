-- Голосовое к объявлению (без распознавания): разовое задание и вакансия
alter table gigs add column if not exists voice_url varchar(500);
alter table gigs add column if not exists voice_duration integer;
alter table vacancies add column if not exists voice_url varchar(500);
alter table vacancies add column if not exists voice_duration integer;
