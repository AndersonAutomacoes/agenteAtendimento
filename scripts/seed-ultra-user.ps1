<#
.SYNOPSIS
  Insere (ou recria) um tenant com profile ULTRA, dados de conversação zerados e registos padrão em
  portal_user, tenant_invite e crm_customer para uso do portal e CRM.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\scripts\seed-ultra-user.ps1 -UseDocker
  powershell -ExecutionPolicy Bypass -File .\scripts\seed-ultra-user.ps1 -FirebaseUid "seuUidFirebaseReal" -InvitePlainCode "MEU-CODIGO-SECRETO"

.NOTES
  - O convite (tenant_invite) guarda SHA-256(hex) do código em claro, igual ao PortalRegistrationService.
  - Ajuste -FirebaseUid ao UID real do utilizador no Firebase para login no portal.
  Requer psql no PATH ou -UseDocker.
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string] $TenantId        = "oficina",
    [string] $Phone           = "557196248348",
    [string] $SystemPrompt    = "Você é a atendente virtual da Oficina Exemplo. Seja cordial e objetivo.",
    [string] $WhatsAppProvider = "EVOLUTION",
    [string] $GoogleCalendarId = "",
    # UID Firebase do operador do portal (deve existir no projeto Firebase ou altere depois na tabela).
    [string] $FirebaseUid     = "bXd6YxUoVQcDwIGNjggF00N60663",
    # Código em claro do convite; o hash SHA-256 é gravado em tenant_invite.code_hash.
    [string] $InvitePlainCode = "SEED-ULTRA-DEV",
    # Linha CRM de demonstração (conversation_id fixo; não conflita com wa-<telefone>).
    [string] $CrmDemoName     = "Cliente demonstração (seed)",
    [string] $PostgresHost     = "localhost",
    [int]    $PostgresPort     = 5433,
    [string] $PostgresUser     = "cerebro",
    [string] $PostgresDatabase = "cerebro",
    [string] $PostgresPassword = "cerebro",
    [switch] $UseDocker,
    [string] $ContainerName    = "cerebro-postgres"
)

$ErrorActionPreference = "Stop"

$digits = [regex]::Replace($Phone, '\D', '')
if ($digits.Length -lt 8) {
    throw "Telefone invalido apos remover nao-digitos: '$Phone' -> '$digits'"
}

if ([string]::IsNullOrWhiteSpace($FirebaseUid)) {
    throw "FirebaseUid nao pode ser vazio. Defina o UID do Firebase (parametro -FirebaseUid)."
}

$sha = [System.Security.Cryptography.SHA256]::Create()
$inviteHashBytes = $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($InvitePlainCode.Trim()))
$inviteHashHex = ($inviteHashBytes | ForEach-Object { $_.ToString("x2") }) -join ""

$tenantSql   = $TenantId.Replace("'", "''")
$promptSql   = $SystemPrompt.Replace("'", "''")
$calendarSql = $GoogleCalendarId.Replace("'", "''")
$fbUidSql    = $FirebaseUid.Replace("'", "''")
$crmNameSql  = $CrmDemoName.Replace("'", "''")

$sqlTemplate = @'
DO $seed$
DECLARE
    v_tenant    text := '__TENANT__';
    v_phone     text := '__PHONE__';
    v_prompt    text := '__PROMPT__';
    v_wa_prov   text := '__WA_PROVIDER__';
    v_gcal      text := '__GCAL__';
    v_fb_uid    text := '__FB_UID__';
    v_inv_hash  text := '__INVITE_HASH__';
    v_crm_name  text := '__CRM_NAME__';
    v_conv      text;
    n           bigint;
BEGIN
    v_phone := regexp_replace(v_phone, '[^0-9]', '', 'g');
    v_conv  := 'wa-' || v_phone;

    -- ========== 1. LIMPAR DADOS DE CONVERSACAO ==========
    DELETE FROM conversation_message WHERE tenant_id = v_tenant;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'conversation_message: % linhas removidas', n;

    DELETE FROM tenant_appointments WHERE tenant_id = v_tenant;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'tenant_appointments: % linhas removidas', n;

    DELETE FROM chat_message WHERE tenant_id = v_tenant;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'chat_message: % linhas removidas', n;

    DELETE FROM analytics_intents WHERE tenant_id = v_tenant;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'analytics_intents: % linhas removidas', n;

    DELETE FROM analytics_stats WHERE tenant_id = v_tenant;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'analytics_stats: % linhas removidas', n;

    DELETE FROM chat_analytics WHERE tenant_id = v_tenant;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'chat_analytics: % linhas removidas', n;

    DELETE FROM crm_customer WHERE tenant_id = v_tenant;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'crm_customer: % linhas removidas', n;

    DELETE FROM conversation WHERE tenant_id = v_tenant;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'conversation: % linhas removidas', n;

    -- Convites e utilizadores do portal (recriados abaixo)
    DELETE FROM tenant_invite WHERE tenant_id = v_tenant;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'tenant_invite (limpeza): % linhas removidas', n;

    DELETE FROM portal_user WHERE tenant_id = v_tenant;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'portal_user (limpeza por tenant): % linhas removidas', n;

    -- Evita violar firebase_uid UNIQUE ao reatribuir o mesmo UID a este tenant
    DELETE FROM portal_user WHERE firebase_uid = v_fb_uid;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'portal_user (limpeza por firebase_uid): % linhas removidas', n;

    -- ========== 2. UPSERT TENANT_CONFIGURATION (COMERCIAL) ==========
    INSERT INTO tenant_configuration (
        tenant_id,
        system_prompt,
        whatsapp_provider_type,
        profile_level,
        google_calendar_id
    ) VALUES (
        v_tenant,
        v_prompt,
        v_wa_prov,
        'COMERCIAL',
        NULLIF(v_gcal, '')
    )
    ON CONFLICT (tenant_id) DO UPDATE SET
        system_prompt          = EXCLUDED.system_prompt,
        whatsapp_provider_type = EXCLUDED.whatsapp_provider_type,
        profile_level          = 'COMERCIAL',
        google_calendar_id     = NULLIF(v_gcal, '');
    RAISE NOTICE 'tenant_configuration: upsert OK (profile=COMERCIAL)';

    -- ========== 3. PORTAL_USER (acesso ao portal com o mesmo tenant) ==========
    INSERT INTO portal_user (id, firebase_uid, tenant_id, profile_level)
    VALUES (gen_random_uuid(), v_fb_uid, v_tenant, 'COMERCIAL');
    RAISE NOTICE 'portal_user: criado firebase_uid=% profile=COMERCIAL', v_fb_uid;

    -- ========== 4. TENANT_INVITE (registo de novos utilizadores com este codigo em claro) ==========
    INSERT INTO tenant_invite (id, tenant_id, code_hash, expires_at, max_uses, uses_count)
    VALUES (
        gen_random_uuid(),
        v_tenant,
        v_inv_hash,
        NULL,
        100,
        0
    );
    RAISE NOTICE 'tenant_invite: criado (max_uses=100, uses_count=0)';

    -- ========== 5. CRM — linha de demonstração (CRM/dashboard com dados minimos) ==========
    INSERT INTO crm_customer (
        tenant_id,
        conversation_id,
        phone_number,
        full_name,
        intent_status,
        lead_score,
        is_converted
    ) VALUES (
        v_tenant,
        'seed-demo',
        NULL,
        v_crm_name,
        'NONE',
        0,
        false
    );
    RAISE NOTICE 'crm_customer: linha demo conversation_id=seed-demo';

    RAISE NOTICE '---------------------------------------------';
    RAISE NOTICE 'Tenant "%"  profile=COMERCIAL  pronto.', v_tenant;
    RAISE NOTICE 'Todos os dados de conversacao foram zerados.';
    RAISE NOTICE 'Telefone mapeado: %  (conversation_id=%)', v_phone, v_conv;
END $seed$;
'@

$sql = $sqlTemplate `
    -replace '__TENANT__',       $tenantSql `
    -replace '__PHONE__',        $digits `
    -replace '__PROMPT__',       $promptSql `
    -replace '__WA_PROVIDER__',  $WhatsAppProvider `
    -replace '__GCAL__',          $calendarSql `
    -replace '__FB_UID__',       $fbUidSql `
    -replace '__INVITE_HASH__',   $inviteHashHex `
    -replace '__CRM_NAME__',     $crmNameSql

$env:PGPASSWORD = $PostgresPassword

if ($UseDocker) {
    if ($PSCmdlet.ShouldProcess($ContainerName, "docker exec psql seed ultra user")) {
        $sql | docker exec -i $ContainerName psql -U $PostgresUser -d $PostgresDatabase -v ON_ERROR_STOP=1
    }
} else {
    if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
        throw "psql nao esta no PATH. Instale o cliente PostgreSQL ou use -UseDocker."
    }
    if ($PSCmdlet.ShouldProcess("${PostgresHost}:${PostgresPort}/${PostgresDatabase}", "psql seed ultra user")) {
        $sql | & psql -h $PostgresHost -p $PostgresPort -U $PostgresUser -d $PostgresDatabase -v ON_ERROR_STOP=1
    }
}

Write-Host ""
Write-Host "OK. Tenant=$TenantId profile=COMERCIAL phone=$digits"
Write-Host "Portal: firebase_uid=$FirebaseUid (COMERCIAL) - use o mesmo UID no Firebase Auth para entrar."
Write-Host "Convite (codigo em claro): $InvitePlainCode  (hash gravado na base)"
Write-Host ('CRM: linha demo conversation_id=seed-demo nome: ' + $CrmDemoName)
