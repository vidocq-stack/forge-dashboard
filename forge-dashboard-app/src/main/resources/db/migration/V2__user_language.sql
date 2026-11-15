-- The language a user chose in the avatar menu (en, fr, de, nl); NULL means the browser's language.
ALTER TABLE user_pref ADD COLUMN language TEXT;
