-- ============================================================
-- PHONESET - Supabase Schema (panel index.html + APK Android)
-- Jalankan di Supabase SQL Editor. Ganti <YOUR_PROJECT_REF> dengan ref proyek Anda.
-- ============================================================

-- ---------- EXTENSIONS ----------
create extension if not exists "uuid-ossp";

-- ---------- PROFILES ----------
create table if not exists public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    email text,
    name text,
    created_at timestamptz default now(),
    provider text default 'email'
);

-- ---------- DEVICES ----------
create table if not exists public.devices (
    id text primary key,
    user_id uuid not null references auth.users (id) on delete cascade,
    name text,
    model text,
    brand text,
    android text,
    battery int,
    last_seen bigint default 0,
    registered_at bigint,
    info jsonb default '{}'::jsonb,
    backup_timestamp bigint default 0
);

create index if not exists devices_user_idx on public.devices (user_id);

-- ---------- COMMANDS ----------
create table if not exists public.commands (
    id text primary key,
    user_id uuid not null references auth.users (id) on delete cascade,
    device_id text not null references public.devices(id) on delete cascade,
    type text not null,
    value jsonb,
    status text default 'pending',
    timestamp bigint not null,
    result jsonb,
    result_at bigint
);

create index if not exists commands_device_idx on public.commands (device_id, status);

-- ---------- DEVICE DATA (kontak, sms, dst...) ----------
create table if not exists public.device_data (
    device_id text not null references public.devices(id) on delete cascade,
    section text not null,
    entry_id text not null,
    data jsonb not null,
    updated_at bigint,
    primary key (device_id, section, entry_id
);

create index if not exists device_data_section_idx on public.device_data (device_id, section);

-- ---------- SUBSCRIPTIONS ----------
create table if not exists public.subscriptions (
    user_id uuid primary key references auth.users (id) on delete cascade,
    active_until bigint default 0,
    pending jsonb default '{}'::jsonb
);

-- ---------- STORAGE BUCKET ----------
insert into storage.buckets (id, name, public)
values ('phoneset-media', 'phoneset-media', true)
on conflict (id) do nothing;

-- ============================================================
-- ROW LEVEL SECURITY
-- ============================================================
alter table public.profiles enable row level security;
alter table public.devices enable row level security;
alter table public.commands enable row level security;
alter table public.device_data enable row level security;
alter table public.subscriptions enable row level security;

-- Profil:hanya pemilik
create policy "profiles_owner_select" on public.profiles for select using (auth.uid((    ) = id);
create policy "profiles_owner_insert" on public.profiles for insert with check (auth.uid((    ) = id);
create policy "profiles_owner_update" on public.profiles for update using (auth.uid((    ) = id);

-- Devices: pemilik penuh
create policy "devices_owner_all" on public.devices for all using (auth.uid((    ) = user_id) with check (auth.uid((    ) = user_id);

-- Commands: pemilik
create policy "commands_owner_all" on public.commands for all using (auth.uid((    ) = user_id) with check (auth.uid((    ) = user_id);

-- Device data: pemilik
create policy "device_data_owner_all" on public.device_data for all using (
    exists (select 1 from public.devices d where d.id = device_data.device_id and d.user_id = auth.uid((    )
) with check (
    exists (select 1 from public.devices d where d.id = device_data.device_id and d.user_id = auth.uid((    )
);

-- Subscriptions: pemilik lihat; admin kelola
create policy "subs_owner_select" on public.subscriptions for select using (auth.uid((    ) = user_id);
create policy "subs_owner_insert" on public.subscriptions for insert with check (auth.uid((    ) = user_id);
create policy "subs_owner_update" on public.subscriptions for update using (auth.uid((    ) = user_id);
create policy "subs_admin_all" on public.subscriptions for all using (
    exists (select 1 from public.profiles p where p.id = auth.uid((    ) and p.email = 'altomediaindonesia@gmail.com'
) with check (
    exists (select 1 from public.profiles p where p.id = auth.uid((    ) and p.email = 'altomediaindonesia@gmail.com'
;

-- Admin lihat semua devices (untuk panel admin)
create policy "devices_admin_select" on public.devices for select using (
    exists (select 1 from public.profiles p where p.id = auth.uid((    ) and p.email = 'altomediaindonesia@gmail.com'
;