-- Vendors capture a business registration number at sign-up (shown to admins in the verification queue).
ALTER TABLE `user` ADD COLUMN `business_registration_number` VARCHAR(50) NULL AFTER `user_type`;

UPDATE `user` SET `business_registration_number` = '2019/123456/07' WHERE `guid` = '00000000-0000-0000-0000-000000000003';
UPDATE `user` SET `business_registration_number` = '2024/654321/07' WHERE `guid` = '00000000-0000-0000-0000-000000000006';

-- Students and faculty must use the university email domain.
UPDATE `user` SET `email` = 'student@university.com' WHERE `guid` = '00000000-0000-0000-0000-000000000002';
UPDATE `user` SET `email` = 'faculty@university.com' WHERE `guid` = '00000000-0000-0000-0000-000000000005';
