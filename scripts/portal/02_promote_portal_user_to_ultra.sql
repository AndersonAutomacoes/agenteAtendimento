-- Promove o utilizador do portal a ULTRA (RBAC: todas as features do plano no backend/UI).
-- O backend lê o perfil a partir de portal_user (não do JWT), por isso basta este UPDATE
-- (após o login, o GET /v1/auth/me deve devolver profileLevel=ULTRA).
--
-- Substitua :firebase_uid pelo UID da conta (Firebase Console → Authentication → utilizador
--   → UID), ou ajuste o WHERE.
--
-- Exemplo:
--   UPDATE portal_user SET profile_level = 'ULTRA', updated_at = NOW()
--   WHERE firebase_uid = 'AbCdEf123...';

-- Descomente e substitua o UID:
/*
UPDATE portal_user
SET profile_level = 'ULTRA', updated_at = NOW()
WHERE firebase_uid = 'xzekmn2yUheTfIkrLRZgAjUxIzn2';
*/