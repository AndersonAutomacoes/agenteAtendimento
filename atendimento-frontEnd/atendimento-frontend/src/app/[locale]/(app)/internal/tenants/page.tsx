 "use client";

import * as React from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Copy, Pencil, Plus, RefreshCw, Trash2 } from "lucide-react";

import { FeatureGuard } from "@/components/plan/feature-guard";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  activateInternalTenant,
  deleteInternalTenant,
  getInternalTenants,
  getPortalUsers,
  getTenantServices,
  getTenantSettings,
  postInternalTenantCreate,
  postInternalTenantInvite,
  putInternalTenant,
  putTenantServices,
  putTenantSettings,
  toUserFacingApiError,
  type InternalTenantListItem,
  type PortalUserRow,
  type ProfileLevel,
  type TenantServiceRow,
} from "@/services/apiService";

const SLOT_MINUTES = [15, 30, 45, 60, 90, 120] as const;
type ServiceDraft = { name: string; duration: string; active: boolean };

export default function InternalTenantsPage() {
  const t = useTranslations("internalTenants");
  const tSettings = useTranslations("settings");
  const tApi = useTranslations("api");
  const translateApi = React.useCallback((key: string) => tApi(key), [tApi]);

  const [tenantId, setTenantId] = React.useState("");
  const [establishmentName, setEstablishmentName] = React.useState("");
  const [customerEmail, setCustomerEmail] = React.useState("");
  const [profileLevel, setProfileLevel] = React.useState<
    "BASIC" | "PRO" | "ULTRA" | "COMERCIAL"
  >("BASIC");
  const [busy, setBusy] = React.useState(false);
  const [inviteCode, setInviteCode] = React.useState("");
  const [resultTenantId, setResultTenantId] = React.useState("");
  const [search, setSearch] = React.useState("");
  const [statusFilter, setStatusFilter] = React.useState<"all" | "active" | "inactive">("all");
  const [tenants, setTenants] = React.useState<InternalTenantListItem[]>([]);
  const [loadingList, setLoadingList] = React.useState(false);
  const [invitingTenant, setInvitingTenant] = React.useState<string | null>(null);
  const [savingTenant, setSavingTenant] = React.useState<string | null>(null);
  const [deactivatingTenant, setDeactivatingTenant] = React.useState<string | null>(null);
  const [editTenantId, setEditTenantId] = React.useState<string | null>(null);
  const [editEstablishmentName, setEditEstablishmentName] = React.useState("");
  const [editCustomerEmail, setEditCustomerEmail] = React.useState("");
  const [editProfileLevel, setEditProfileLevel] = React.useState<ProfileLevel>("BASIC");
  const [selectedTenantId, setSelectedTenantId] = React.useState<string | null>(null);
  const [activeTab, setActiveTab] = React.useState<"tenants" | "clientes">("tenants");

  const [loadingTenantData, setLoadingTenantData] = React.useState(false);
  const [savingTenantData, setSavingTenantData] = React.useState(false);
  const [savingServices, setSavingServices] = React.useState(false);
  const [portalUsers, setPortalUsers] = React.useState<PortalUserRow[]>([]);
  const [serviceRows, setServiceRows] = React.useState<ServiceDraft[]>([]);
  const [personality, setPersonality] = React.useState("");
  const [establishmentNameClient, setEstablishmentNameClient] = React.useState("");
  const [businessAddress, setBusinessAddress] = React.useState("");
  const [openingHours, setOpeningHours] = React.useState("");
  const [businessContacts, setBusinessContacts] = React.useState("");
  const [businessFacilities, setBusinessFacilities] = React.useState("");
  const [defaultSlotMinutes, setDefaultSlotMinutes] = React.useState(30);
  const [billingCompliant, setBillingCompliant] = React.useState(true);
  const [googleCalendarId, setGoogleCalendarId] = React.useState("");
  const [calendarAccessNotes, setCalendarAccessNotes] = React.useState("");
  const [spreadsheetUrl, setSpreadsheetUrl] = React.useState("");
  const [whatsappBusinessNumber, setWhatsappBusinessNumber] = React.useState("");
  const [inviteRecipientEmail, setInviteRecipientEmail] = React.useState("");
  const [inviteOpen, setInviteOpen] = React.useState(false);
  const [invitePayload, setInvitePayload] = React.useState<{
    code: string;
    message: string;
  } | null>(null);

  const refreshList = React.useCallback(async () => {
    setLoadingList(true);
    try {
      const rows = await getInternalTenants(search);
      setTenants(rows);
    } catch (e) {
      toast.error(toUserFacingApiError(e, translateApi));
    } finally {
      setLoadingList(false);
    }
  }, [search, translateApi]);

  React.useEffect(() => {
    void refreshList();
  }, [refreshList]);

  const loadTenantClientData = React.useCallback(
    async (tenantIdToLoad: string) => {
      setLoadingTenantData(true);
      try {
        const [settings, users, services] = await Promise.all([
          getTenantSettings(tenantIdToLoad),
          getPortalUsers(tenantIdToLoad),
          getTenantServices(tenantIdToLoad),
        ]);
        setPersonality(settings.systemPrompt);
        setEstablishmentNameClient(settings.establishmentName ?? "");
        setBusinessAddress(settings.businessAddress ?? "");
        setOpeningHours(settings.openingHours ?? "");
        setBusinessContacts(settings.businessContacts ?? "");
        setInviteRecipientEmail(extractEmailFromContacts(settings.businessContacts ?? ""));
        setBusinessFacilities(settings.businessFacilities ?? "");
        setDefaultSlotMinutes(
          settings.defaultAppointmentMinutes > 0
            ? settings.defaultAppointmentMinutes
            : 30,
        );
        setBillingCompliant(settings.billingCompliant);
        setGoogleCalendarId(settings.googleCalendarId ?? "");
        setCalendarAccessNotes(settings.calendarAccessNotes ?? "");
        setSpreadsheetUrl(settings.spreadsheetUrl ?? "");
        setWhatsappBusinessNumber(settings.whatsappBusinessNumber ?? "");
        setPortalUsers(users);
        setServiceRows(
          services.length > 0
            ? services.map((s: TenantServiceRow) => ({
                name: s.name,
                duration:
                  s.durationMinutes != null ? String(s.durationMinutes) : "",
                active: s.active,
              }))
            : [{ name: "", duration: "30", active: true }],
        );
      } catch (e) {
        toast.error(toUserFacingApiError(e, translateApi));
      } finally {
        setLoadingTenantData(false);
      }
    },
    [translateApi],
  );

  const submit = async () => {
    if (!tenantId.trim()) {
      toast.error(t("tenantRequired"));
      return;
    }
    setBusy(true);
    try {
      const res = await postInternalTenantCreate({
        tenantId: tenantId.trim(),
        establishmentName,
        customerEmail,
        profileLevel,
      });
      setInviteCode(res.inviteCode);
      setResultTenantId(res.tenantId);
      toast.success(t("createdOk"));
      await refreshList();
    } catch (e) {
      toast.error(toUserFacingApiError(e, translateApi));
    } finally {
      setBusy(false);
    }
  };

  const createExtraInvite = async (tenant: string) => {
    setInvitingTenant(tenant);
    try {
      const res = await postInternalTenantInvite(tenant, 5);
      setInviteCode(res.inviteCode);
      setResultTenantId(res.tenantId);
      toast.success(t("inviteCreated"));
    } catch (e) {
      toast.error(toUserFacingApiError(e, translateApi));
    } finally {
      setInvitingTenant(null);
    }
  };

  const openEdit = (tenant: InternalTenantListItem) => {
    setEditTenantId(tenant.tenantId);
    setEditEstablishmentName(tenant.establishmentName ?? "");
    setEditCustomerEmail(extractEmailFromContacts(tenant.contacts ?? ""));
    setEditProfileLevel(tenant.profileLevel);
  };

  const saveEdit = async () => {
    if (!editTenantId) return;
    setSavingTenant(editTenantId);
    try {
      await putInternalTenant(editTenantId, {
        establishmentName: editEstablishmentName,
        customerEmail: editCustomerEmail,
        profileLevel: editProfileLevel,
      });
      toast.success(t("createdOk"));
      setEditTenantId(null);
      await refreshList();
      if (selectedTenantId === editTenantId) {
        await loadTenantClientData(editTenantId);
      }
    } catch (e) {
      toast.error(toUserFacingApiError(e, translateApi));
    } finally {
      setSavingTenant(null);
    }
  };

  const deactivate = async (tenant: InternalTenantListItem) => {
    if (!window.confirm(`${t("confirmDeactivate")} ${tenant.tenantId}?`)) {
      return;
    }
    setDeactivatingTenant(tenant.tenantId);
    try {
      await deleteInternalTenant(tenant.tenantId);
      toast.success(t("deactivatedOk"));
      await refreshList();
    } catch (e) {
      toast.error(toUserFacingApiError(e, translateApi));
    } finally {
      setDeactivatingTenant(null);
    }
  };

  const activate = async (tenant: InternalTenantListItem) => {
    setDeactivatingTenant(tenant.tenantId);
    try {
      await activateInternalTenant(tenant.tenantId);
      toast.success(t("activatedOk"));
      await refreshList();
    } catch (e) {
      toast.error(toUserFacingApiError(e, translateApi));
    } finally {
      setDeactivatingTenant(null);
    }
  };

  const saveTenantData = async () => {
    const tid = selectedTenantId?.trim();
    if (!tid) {
      return;
    }
    setSavingTenantData(true);
    try {
      await putTenantSettings({
        tenantId: tid,
        systemPrompt: personality,
        whatsappProviderType: "SIMULATED",
        whatsappApiKey: "",
        whatsappInstanceId: "",
        whatsappBaseUrl: "",
        googleCalendarId: nullIfEmpty(googleCalendarId),
        establishmentName: nullIfEmpty(establishmentNameClient),
        businessAddress: nullIfEmpty(businessAddress),
        openingHours: nullIfEmpty(openingHours),
        businessContacts: nullIfEmpty(businessContacts),
        businessFacilities: nullIfEmpty(businessFacilities),
        defaultAppointmentMinutes: defaultSlotMinutes,
        billingCompliant,
        calendarAccessNotes: nullIfEmpty(calendarAccessNotes),
        spreadsheetUrl: nullIfEmpty(spreadsheetUrl),
        whatsappBusinessNumber: nullIfEmpty(whatsappBusinessNumber),
      });
      toast.success(tSettings("toastSaved"));
      await refreshList();
    } catch (e) {
      toast.error(toUserFacingApiError(e, translateApi));
    } finally {
      setSavingTenantData(false);
    }
  };

  const saveServices = async () => {
    const tid = selectedTenantId?.trim();
    if (!tid) {
      return;
    }
    setSavingServices(true);
    try {
      const items = serviceRows
        .filter((r) => r.name.trim().length > 0)
        .map((r) => {
          const d = parseInt(r.duration, 10);
          return {
            name: r.name.trim(),
            durationMinutes: Number.isNaN(d) || d <= 0 ? null : d,
            active: r.active,
          };
        });
      await putTenantServices(items, tid);
      toast.success(tSettings("toastServicesSaved"));
      await loadTenantClientData(tid);
    } catch (e) {
      toast.error(toUserFacingApiError(e, translateApi));
    } finally {
      setSavingServices(false);
    }
  };

  const createInvite = async () => {
    const tid = selectedTenantId?.trim();
    if (!tid) return;
    const inviteEmail = inviteRecipientEmail.trim();
    if (!inviteEmail) {
      toast.error(tSettings("inviteEmailRequired"));
      return;
    }
    if (!isValidEmail(inviteEmail)) {
      toast.error(tSettings("inviteEmailInvalid"));
      return;
    }
    setInvitingTenant(tid);
    try {
      const r = await postInternalTenantInvite(tid, 5, inviteEmail);
      setInvitePayload({ code: r.inviteCode, message: r.message });
      setInviteOpen(true);
    } catch (e) {
      toast.error(toUserFacingApiError(e, translateApi));
    } finally {
      setInvitingTenant(null);
    }
  };

  const filteredTenants = React.useMemo(
    () =>
      tenants.filter((x) => {
        if (statusFilter === "active") return x.active;
        if (statusFilter === "inactive") return !x.active;
        return true;
      }),
    [tenants, statusFilter],
  );

  return (
    <FeatureGuard requiredPlan="enterprise" requiredProfile="COMERCIAL">
      <div className="mx-auto max-w-6xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{t("title")}</h1>
        <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("formTitle")}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="tenantId">{t("tenantId")}</Label>
            <Input
              id="tenantId"
              value={tenantId}
              onChange={(e) => setTenantId(e.target.value)}
              placeholder="oficina-centro-sp"
              disabled={busy}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="estName">{t("establishmentName")}</Label>
            <Input
              id="estName"
              value={establishmentName}
              onChange={(e) => setEstablishmentName(e.target.value)}
              placeholder={t("establishmentPlaceholder")}
              disabled={busy}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="custEmail">{t("customerEmail")}</Label>
            <Input
              id="custEmail"
              type="email"
              value={customerEmail}
              onChange={(e) => setCustomerEmail(e.target.value)}
              placeholder="cliente@empresa.com"
              disabled={busy}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="profile">{t("plan")}</Label>
            <select
              id="profile"
              className="flex h-9 w-full rounded-xl border border-input bg-transparent px-3 py-1 text-sm"
              value={profileLevel}
              onChange={(e) =>
                setProfileLevel(
                  e.target.value as "BASIC" | "PRO" | "ULTRA" | "COMERCIAL",
                )
              }
              disabled={busy}
            >
              <option value="BASIC">BASIC</option>
              <option value="PRO">PRO</option>
              <option value="ULTRA">ULTRA</option>
              <option value="COMERCIAL">COMERCIAL</option>
            </select>
          </div>
          <Button type="button" onClick={() => void submit()} disabled={busy}>
            {busy ? t("creating") : t("create")}
          </Button>
        </CardContent>
      </Card>

      <div className="flex flex-wrap gap-2 rounded-xl border border-border/70 bg-muted/20 p-1">
        <Button
          type="button"
          variant={activeTab === "tenants" ? "default" : "ghost"}
          onClick={() => setActiveTab("tenants")}
        >
          {t("listTitle")}
        </Button>
        <Button
          type="button"
          variant={activeTab === "clientes" ? "default" : "ghost"}
          onClick={() => setActiveTab("clientes")}
          disabled={!selectedTenantId}
        >
          {t("clientesTab")}
        </Button>
      </div>

      {inviteCode ? (
        <Card>
          <CardHeader>
            <CardTitle>{t("resultTitle")}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            <p className="text-sm text-muted-foreground">
              {t("resultTenant")} <span className="font-medium">{resultTenantId}</span>
            </p>
            <div className="flex items-center gap-2">
              <code className="flex-1 rounded-md bg-muted px-2 py-1">{inviteCode}</code>
              <Button
                type="button"
                size="icon"
                variant="outline"
                onClick={async () => {
                  await navigator.clipboard.writeText(inviteCode);
                  toast.success(t("copied"));
                }}
              >
                <Copy className="h-4 w-4" />
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">{t("manualEmailHint")}</p>
          </CardContent>
        </Card>
      ) : null}

      {activeTab === "tenants" ? (
      <Card>
        <CardHeader>
          <CardTitle>{t("listTitle")}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="flex flex-wrap gap-2">
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder={t("searchPlaceholder")}
            />
            <select
              className="h-9 rounded-xl border border-input bg-transparent px-3 py-1 text-sm"
              value={statusFilter}
              onChange={(e) =>
                setStatusFilter(e.target.value as "all" | "active" | "inactive")
              }
            >
              <option value="all">{t("statusAll")}</option>
              <option value="active">{t("statusActive")}</option>
              <option value="inactive">{t("statusInactive")}</option>
            </select>
            <Button
              type="button"
              variant="outline"
              onClick={() => void refreshList()}
              disabled={loadingList}
            >
              <RefreshCw className="mr-2 h-4 w-4" />
              {t("refresh")}
            </Button>
          </div>
          <div className="space-y-2">
            {tenants.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t("emptyList")}</p>
            ) : (
              <div className="grid gap-3 md:grid-cols-2">
              {filteredTenants.map((x) => (
                <div
                  key={x.tenantId}
                  className="rounded-xl border border-border/70 bg-muted/20 p-3"
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div>
                      <p className="font-medium">{x.tenantId}</p>
                      <p className="text-xs text-muted-foreground">
                        {x.establishmentName || "—"} · {x.profileLevel} ·{" "}
                        {x.billingCompliant ? t("adimplent") : t("inadimplent")}
                      </p>
                      <p className="text-xs">
                        <span
                          className={
                            x.active
                              ? "rounded-full bg-emerald-100 px-2 py-0.5 text-emerald-800"
                              : "rounded-full bg-amber-100 px-2 py-0.5 text-amber-800"
                          }
                        >
                          {x.active ? t("statusActive") : t("statusInactive")}
                        </span>
                      </p>
                      <p className="text-xs text-muted-foreground">
                        {t("monthlyUsage")}: {x.monthlyAppointmentsUsed} /{" "}
                        {x.monthlyAppointmentsLimit ?? t("unlimited")}
                      </p>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        onClick={() => openEdit(x)}
                      >
                        <Pencil className="mr-1 h-4 w-4" />
                        {t("editTenant")}
                      </Button>
                      <Button
                        type="button"
                        size="sm"
                        variant="secondary"
                        onClick={() => void createExtraInvite(x.tenantId)}
                        disabled={invitingTenant === x.tenantId}
                      >
                        {invitingTenant === x.tenantId
                          ? t("creating")
                          : t("newInviteUser")}
                      </Button>
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        onClick={() => {
                          setSelectedTenantId(x.tenantId);
                          setActiveTab("clientes");
                          void loadTenantClientData(x.tenantId);
                        }}
                      >
                        {t("openClientTab")}
                      </Button>
                      {!x.active ? (
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          onClick={() => void activate(x)}
                          disabled={deactivatingTenant === x.tenantId}
                        >
                          {t("activate")}
                        </Button>
                      ) : (
                        <Button
                          type="button"
                          size="sm"
                          variant="destructive"
                          onClick={() => void deactivate(x)}
                          disabled={deactivatingTenant === x.tenantId}
                        >
                          <Trash2 className="mr-1 h-4 w-4" />
                          {t("deactivate")}
                        </Button>
                      )}
                    </div>
                  </div>
                  {x.contacts ? (
                    <p className="mt-2 text-xs text-muted-foreground break-all">
                      {x.contacts}
                    </p>
                  ) : null}
                </div>
              ))}
              </div>
            )}
          </div>
        </CardContent>
      </Card>
      ) : (
      <div className="space-y-6">
        {!selectedTenantId ? (
          <Card>
            <CardContent className="pt-6 text-sm text-muted-foreground">
              {t("selectTenantFirst")}
            </CardContent>
          </Card>
        ) : (
          <>
            <Card>
              <CardHeader>
                <CardTitle>{t("clientTabTitle", { tenantId: selectedTenantId })}</CardTitle>
              </CardHeader>
              <CardContent>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => void loadTenantClientData(selectedTenantId)}
                  disabled={loadingTenantData}
                >
                  <RefreshCw className="mr-2 h-4 w-4" />
                  {t("refresh")}
                </Button>
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>{tSettings("sectionBusiness")}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <Label>{tSettings("establishmentName")}</Label>
                  <Input
                    value={establishmentNameClient}
                    onChange={(e) => setEstablishmentNameClient(e.target.value)}
                    disabled={loadingTenantData || savingTenantData}
                  />
                </div>
                <div className="space-y-2">
                  <Label>{tSettings("businessAddress")}</Label>
                  <Textarea value={businessAddress} onChange={(e) => setBusinessAddress(e.target.value)} />
                </div>
                <div className="space-y-2">
                  <Label>{tSettings("openingHours")}</Label>
                  <Textarea value={openingHours} onChange={(e) => setOpeningHours(e.target.value)} />
                </div>
                <div className="space-y-2">
                  <Label>{tSettings("businessContacts")}</Label>
                  <Textarea value={businessContacts} onChange={(e) => setBusinessContacts(e.target.value)} />
                </div>
                <div className="space-y-2">
                  <Label>{tSettings("businessFacilities")}</Label>
                  <Textarea value={businessFacilities} onChange={(e) => setBusinessFacilities(e.target.value)} />
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>{tSettings("sectionBilling")}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={billingCompliant}
                    onChange={(e) => setBillingCompliant(e.target.checked)}
                  />
                  {tSettings("billingCompliant")}
                </label>
                <div className="space-y-2">
                  <Label>{tSettings("defaultSlot")}</Label>
                  <select
                    className="h-9 w-full rounded-xl border border-input bg-transparent px-3 py-1 text-sm"
                    value={String(defaultSlotMinutes)}
                    onChange={(e) => setDefaultSlotMinutes(Number(e.target.value))}
                  >
                    {SLOT_MINUTES.map((m) => (
                      <option key={m} value={m}>
                        {m} {tSettings("minutesUnit")}
                      </option>
                    ))}
                  </select>
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>{tSettings("sectionCalendar")}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="space-y-2">
                  <Label>{tSettings("googleCalendarId")}</Label>
                  <Input value={googleCalendarId} onChange={(e) => setGoogleCalendarId(e.target.value)} />
                </div>
                <div className="space-y-2">
                  <Label>{tSettings("spreadsheetUrl")}</Label>
                  <Input value={spreadsheetUrl} onChange={(e) => setSpreadsheetUrl(e.target.value)} />
                </div>
                <div className="space-y-2">
                  <Label>{tSettings("calendarAccessNotes")}</Label>
                  <Textarea value={calendarAccessNotes} onChange={(e) => setCalendarAccessNotes(e.target.value)} />
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>{tSettings("sectionWhatsappNumber")}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-2">
                <Label>{tSettings("whatsappBusinessNumber")}</Label>
                <Input value={whatsappBusinessNumber} onChange={(e) => setWhatsappBusinessNumber(e.target.value)} />
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>{tSettings("sectionServices")}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {serviceRows.map((row, i) => (
                  <div
                    key={`${i}-${selectedTenantId}`}
                    className="grid gap-2 rounded-xl border border-border p-3 sm:grid-cols-[1fr,120px,auto,auto] sm:items-end"
                  >
                    <div className="space-y-1">
                      <Label className="text-xs">{tSettings("serviceName")}</Label>
                      <Input
                        value={row.name}
                        onChange={(e) => {
                          const next = [...serviceRows];
                          next[i] = { ...next[i], name: e.target.value };
                          setServiceRows(next);
                        }}
                      />
                    </div>
                    <div className="space-y-1">
                      <Label className="text-xs">{tSettings("serviceDuration")}</Label>
                      <Input
                        type="number"
                        min={0}
                        value={row.duration}
                        onChange={(e) => {
                          const next = [...serviceRows];
                          next[i] = { ...next[i], duration: e.target.value };
                          setServiceRows(next);
                        }}
                      />
                    </div>
                    <label className="flex items-center gap-2 text-sm">
                      <input
                        type="checkbox"
                        checked={row.active}
                        onChange={(e) => {
                          const next = [...serviceRows];
                          next[i] = { ...next[i], active: e.target.checked };
                          setServiceRows(next);
                        }}
                      />
                      {tSettings("serviceActive")}
                    </label>
                    <Button
                      type="button"
                      size="icon"
                      variant="ghost"
                      onClick={() => setServiceRows(serviceRows.filter((_, j) => j !== i))}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                ))}
                <div className="flex flex-wrap gap-2">
                  <Button
                    type="button"
                    variant="secondary"
                    onClick={() =>
                      setServiceRows([...serviceRows, { name: "", duration: "30", active: true }])
                    }
                  >
                    <Plus className="mr-1 h-4 w-4" />
                    {tSettings("addService")}
                  </Button>
                  <Button type="button" onClick={() => void saveServices()} disabled={savingServices}>
                    {savingServices ? tSettings("savingServices") : tSettings("saveServices")}
                  </Button>
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>{tSettings("sectionPortalUsers")}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-2">
                {portalUsers.length === 0 ? (
                  <p className="text-sm text-muted-foreground">{tSettings("noPortalUsers")}</p>
                ) : (
                  <ul className="space-y-1 text-sm">
                    {portalUsers.map((u) => (
                      <li key={u.id} className="rounded-lg border border-border/60 bg-muted/20 px-3 py-2">
                        <span className="font-mono text-xs break-all">{u.firebaseUid}</span>
                        <span className="ml-2 text-muted-foreground">· {u.profileLevel}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>{tSettings("sectionInvite")}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="space-y-2">
                  <Label htmlFor="invite-email">{tSettings("inviteEmailLabel")}</Label>
                  <Input
                    id="invite-email"
                    type="email"
                    value={inviteRecipientEmail}
                    onChange={(e) => setInviteRecipientEmail(e.target.value)}
                    placeholder={tSettings("inviteEmailPlaceholder")}
                    disabled={invitingTenant === selectedTenantId}
                  />
                </div>
                <Button type="button" variant="secondary" onClick={() => void createInvite()} disabled={invitingTenant === selectedTenantId}>
                  {tSettings("createInvite")}
                </Button>
              </CardContent>
            </Card>
            <div className="flex items-center gap-2">
              <Button type="button" onClick={() => void saveTenantData()} disabled={savingTenantData || loadingTenantData}>
                {savingTenantData ? tSettings("saving") : tSettings("save")}
              </Button>
            </div>
          </>
        )}
      </div>
      )}
      <Dialog open={Boolean(editTenantId)} onOpenChange={(open) => !open && setEditTenantId(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("editTenant")}</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-2">
              <Label>{t("establishmentName")}</Label>
              <Input value={editEstablishmentName} onChange={(e) => setEditEstablishmentName(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>{t("customerEmail")}</Label>
              <Input value={editCustomerEmail} onChange={(e) => setEditCustomerEmail(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>{t("plan")}</Label>
              <select
                className="h-9 w-full rounded-xl border border-input bg-transparent px-3 py-1 text-sm"
                value={editProfileLevel}
                onChange={(e) => setEditProfileLevel(e.target.value as ProfileLevel)}
              >
                <option value="BASIC">BASIC</option>
                <option value="PRO">PRO</option>
                <option value="ULTRA">ULTRA</option>
                <option value="COMERCIAL">COMERCIAL</option>
              </select>
            </div>
            <Button type="button" onClick={() => void saveEdit()} disabled={savingTenant === editTenantId}>
              {savingTenant === editTenantId ? t("creating") : t("saveTenant")}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
      <Dialog open={inviteOpen} onOpenChange={setInviteOpen}>
        <DialogContent className="rounded-2xl sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{tSettings("inviteDialogTitle")}</DialogTitle>
          </DialogHeader>
          {invitePayload && (
            <div className="space-y-3 text-sm">
              <div className="flex flex-wrap items-center gap-2">
                <code className="flex-1 rounded-md bg-muted px-2 py-1 font-mono text-sm">
                  {invitePayload.code}
                </code>
                <Button
                  type="button"
                  size="icon"
                  variant="outline"
                  onClick={async () => {
                    try {
                      await navigator.clipboard.writeText(invitePayload.code);
                      toast.success(t("copied"));
                    } catch {
                      /* ignore */
                    }
                  }}
                >
                  <Copy className="h-4 w-4" />
                </Button>
              </div>
              <p className="text-muted-foreground">{invitePayload.message}</p>
            </div>
          )}
        </DialogContent>
      </Dialog>
      </div>
    </FeatureGuard>
  );
}

function nullIfEmpty(s: string): string | null {
  return s.trim() ? s : null;
}

function extractEmailFromContacts(contacts: string): string {
  const line = contacts
    .split(/\r?\n/)
    .map((x) => x.trim())
    .find((x) => x.toLowerCase().startsWith("email:"));
  if (!line) return "";
  return line.slice(6).trim();
}

function isValidEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}
