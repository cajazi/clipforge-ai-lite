# Authentication Setup

## 1. Supabase Dashboard

Go to: Authentication → URL Configuration → Redirect URLs

Add:
```
clipforgeai://auth-callback
```

## 2. Run SQL in Supabase SQL Editor

```sql
-- profiles table
create table if not exists profiles (
    id           uuid primary key references auth.users(id) on delete cascade,
    email        text not null,
    display_name text not null default '',
    avatar_url   text,
    plan         text not null default 'free',
    role         text not null default 'user',
    created_at   timestamptz default now(),
    updated_at   timestamptz default now()
);

-- RLS
alter table profiles enable row level security;

create policy "Users can view own profile"
    on profiles for select using (auth.uid() = id);

create policy "Users can update own profile"
    on profiles for update using (auth.uid() = id);

create policy "Users can insert own profile"
    on profiles for insert with check (auth.uid() = id);
```

## 3. AndroidManifest.xml

Inside the MainActivity <activity> block, add:
```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <category android:name="android.intent.category.BROWSABLE"/>
    <data android:scheme="clipforgeai" android:host="auth-callback"/>
</intent-filter>
```

## 4. Developer account

cossybest24@gmail.com → auto receives role=admin, plan=pro from ProfileRepositoryImpl
