<#
.SYNOPSIS
  Um único script: zera na base PostgreSQL os dados de conversação de um cliente (mensagens, CRM, analytics, agendamentos, estado do bot).

.EXAMPLE
  # Windows PowerShell 5.1 (padrão; use isto se "pwsh" não existir):
  powershell -ExecutionPolicy Bypass -File .\scripts\reset-client-conversation.ps1 -TenantId "meu-tenant" -Phone "+55 11 98765-4321"
  # PowerShell 7+ (se instalado):
  pwsh -File .\scripts\reset-client-conversation.ps1 -TenantId "meu-tenant" -Phone "11987654321" -UseDocker

.NOTES
  Requer psql no PATH ou -UseDocker (container cerebro-postgres por defeito).
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true)]
    [string] $TenantId,
    [Parameter(Mandatory = $true)]
    [string] $Phone,
    [string] $PostgresHost = "localhost",
    [int] $PostgresPort = 5433,
    [string] $PostgresUser = "cerebro",
    [string] $PostgresDatabase = "cerebro",
    [string] $PostgresPassword = "cerebro",
    [switch] $UseDocker,
    [string] $ContainerName = "cerebro-postgres"
)

$ErrorActionPreference = "Stop"

$digits = [regex]::Replace($Phone, '\D', '')
if ($digits.Length -lt 8) {
    throw "Telefone inválido após remover não-dígitos: '$Phone' -> '$digits'"
}

$tenantSql = $TenantId.Replace("'", "''")

# SQL embutido (placeholders __TENANT__ / __PHONE__ — sem $ no here-string para não confundir o PowerShell)
$sqlTemplate = @'
DO $reset$
DECLARE
    v_tenant text := '__TENANT__';
    v_phone  text := '__PHONE__';
    v_conv   text;
    n        bigint;
BEGIN
    v_phone := regexp_replace(v_phone, '[^0-9]', '', 'g');
    IF length(v_phone) < 8 THEN
        RAISE EXCEPTION 'Telefone inválido';
    END IF;
    v_conv := 'wa-' || v_phone;

    DELETE FROM conversation_message WHERE tenant_id = v_tenant AND conversation_id = v_conv;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'conversation_message: % linhas', n;

    DELETE FROM tenant_appointments WHERE tenant_id = v_tenant AND conversation_id = v_conv;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'tenant_appointments: % linhas', n;

    DELETE FROM chat_message WHERE tenant_id = v_tenant AND phone_number = v_phone;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'chat_message: % linhas', n;

    DELETE FROM analytics_intents WHERE tenant_id = v_tenant AND phone_number = v_phone;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'analytics_intents: % linhas', n;

    DELETE FROM analytics_stats WHERE tenant_id = v_tenant AND phone_number = v_phone;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'analytics_stats: % linhas', n;

    DELETE FROM chat_analytics WHERE tenant_id = v_tenant AND phone_number = v_phone;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'chat_analytics: % linhas', n;

    DELETE FROM crm_customer
    WHERE tenant_id = v_tenant
      AND (phone_number = v_phone OR conversation_id = v_conv);
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'crm_customer: % linhas', n;

    DELETE FROM conversation WHERE tenant_id = v_tenant AND phone_number = v_phone;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'conversation: % linhas', n;

    RAISE NOTICE 'Concluído. conversation_id=%', v_conv;
END $reset$;
'@

$sql = $sqlTemplate.Replace('__TENANT__', $tenantSql).Replace('__PHONE__', $digits)

$env:PGPASSWORD = $PostgresPassword

if ($UseDocker) {
    if ($PSCmdlet.ShouldProcess($ContainerName, "docker exec psql reset client")) {
        $sql | docker exec -i $ContainerName psql -U $PostgresUser -d $PostgresDatabase -v ON_ERROR_STOP=1
    }
} else {
    if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
        throw "psql não está no PATH. Instale o cliente PostgreSQL ou use -UseDocker."
    }
    if ($PSCmdlet.ShouldProcess("${PostgresHost}:${PostgresPort}/${PostgresDatabase}", "psql reset client")) {
        $sql | & psql -h $PostgresHost -p $PostgresPort -U $PostgresUser -d $PostgresDatabase -v ON_ERROR_STOP=1
    }
}

Write-Host "OK. Tenant=$TenantId phone=$digits (conversation_id=wa-$digits)"
