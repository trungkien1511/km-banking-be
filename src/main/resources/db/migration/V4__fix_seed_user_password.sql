-- Fix incorrect password hash for seed user john_doe
-- The previous hash did not match 'Password123'. 
-- This script updates the existing user's password to the correctly generated BCrypt hash for 'Password123'.

UPDATE users 
SET password_hash = '$2a$10$M2KVSPFrdjNjx36GxOdwW..NcmENyPkeCV9R4re448vGOdgJ4mEUO'
WHERE username = 'john_doe';
