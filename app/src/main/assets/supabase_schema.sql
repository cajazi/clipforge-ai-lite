-- ClipForge AI Lite — Supabase Schema
-- Run this in your Supabase SQL Editor

-- Enable UUID extension
create extension if not exists "uuid-ossp";

-- Profiles table used by app auth state
create table if not exists profiles (
    id           uuid primary key references auth.users(id) on delete cascade,
    email        text not null,
    display_name text not null,
    avatar_url   text,
    plan         text not null default 'free',
    role         text not null default 'user',
    created_at   timestamptz default now(),
    updated_at   timestamptz default now()
);

-- Projects table
create table if not exists projects (
    id              text primary key,
    title           text not null,
    aspect_ratio    text not null default 'RATIO_9_16',
    export_quality  text not null default 'QUALITY_720P',
    plan_type       text not null default 'FREE',
    thumbnail_url   text,
    user_id         uuid references auth.users(id) on delete cascade default auth.uid(),
    created_at      timestamptz default now(),
    updated_at      timestamptz default now()
);

alter table projects alter column user_id set default auth.uid();

-- Media assets table
create table if not exists media_assets (
    id              text primary key,
    project_id      text references projects(id) on delete cascade,
    media_type      text not null,
    storage_path    text,
    public_url      text,
    duration_ms     bigint,
    file_size       bigint default 0,
    mime_type       text,
    created_at      timestamptz default now()
);

-- Render jobs table
create table if not exists render_jobs (
    id               text primary key,
    project_id       text references projects(id) on delete cascade,
    status           text not null default 'QUEUED',
    export_quality   text not null default '720p',
    add_watermark    boolean default true,
    output_url       text,
    error_message    text,
    progress_percent integer default 0,
    created_at       timestamptz default now(),
    completed_at     timestamptz
);

-- Row Level Security (enable after testing)
alter table profiles enable row level security;
alter table projects enable row level security;
alter table media_assets enable row level security;
alter table render_jobs enable row level security;

drop policy if exists "Users can view own profile" on profiles;
create policy "Users can view own profile"
    on profiles for select using (auth.uid() = id);

drop policy if exists "Users can insert own profile" on profiles;
create policy "Users can insert own profile"
    on profiles for insert with check (auth.uid() = id);

drop policy if exists "Users can update own profile" on profiles;
create policy "Users can update own profile"
    on profiles for update using (auth.uid() = id) with check (auth.uid() = id);

drop policy if exists "Users can manage own projects" on projects;
create policy "Users can manage own projects"
    on projects for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "Users can manage media for own projects" on media_assets;
create policy "Users can manage media for own projects"
    on media_assets for all using (
        exists (select 1 from projects where projects.id = media_assets.project_id and projects.user_id = auth.uid())
    ) with check (
        exists (select 1 from projects where projects.id = media_assets.project_id and projects.user_id = auth.uid())
    );

drop policy if exists "Users can manage render jobs for own projects" on render_jobs;
create policy "Users can manage render jobs for own projects"
    on render_jobs for all using (
        exists (select 1 from projects where projects.id = render_jobs.project_id and projects.user_id = auth.uid())
    ) with check (
        exists (select 1 from projects where projects.id = render_jobs.project_id and projects.user_id = auth.uid())
    );

-- Storage buckets (run in Supabase Storage dashboard)
-- Create bucket: "media"   (private)
-- Create bucket: "exports" (public)

-- Indexes
create index if not exists idx_projects_updated   on projects(updated_at desc);
create index if not exists idx_media_project      on media_assets(project_id);
create index if not exists idx_render_project     on render_jobs(project_id);
create index if not exists idx_render_status      on render_jobs(status);
