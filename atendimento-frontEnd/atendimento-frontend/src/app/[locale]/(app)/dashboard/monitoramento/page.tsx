"use client";

import { Hand, RefreshCw } from "lucide-react";
import * as React from "react";
import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { toast } from "sonner";

import { ChatBubble } from "@/components/chat/chat-bubble";
import { CustomerRecordDialog } from "@/components/crm/customer-record-dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toBcp47ForDates } from "@/lib/intl-locale";
import { cn } from "@/lib/utils";
import {
  enableBotForConversation,
  getChatMessages,
  humanHandoffConversation,
  lookupUpcomingAppointmentsByPhones,
  retryChatMessage,
  sendHumanMonitorMessage,
  toUserFacingApiError,
  type ChatMessageItem,
} from "@/services/apiService";

const TENANT_STORAGE_KEY = "cerebro-tenant-id";
const POLL_MS = 5_000;
const APPOINTMENT_LOOKUP_CHUNK = 40;

type MonitorContact = {
  phoneNumber: string;
  displayPhone: string;
  displayTitle: string;
  lastPreview: string;
  lastMessageAt: string;
  /** false = atendimento humano (IA pausada) */
  botEnabled: boolean;
};

type UiThreadMessage = {
  id: string;
  role: "user" | "assistant";
  text: string;
  sentAt: string;
  status: ChatMessageItem["status"];
};

function latestContactNameForPhone(
  messages: ChatMessageItem[],
  phoneNumber: string,
): string | null {
  const thread = messages
    .filter((m) => m.phoneNumber === phoneNumber)
    .sort(
      (a, b) =>
        new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime(),
    );
  for (let i = thread.length - 1; i >= 0; i--) {
    const n = thread[i]!.contactDisplayName?.trim();
    if (n) return n;
  }
  return null;
}

function isConversationPending(
  messages: ChatMessageItem[],
  phoneNumber: string,
): boolean {
  const thread = messages
    .filter((m) => m.phoneNumber === phoneNumber)
    .sort((a, b) => {
      const ta = new Date(a.timestamp).getTime();
      const tb = new Date(b.timestamp).getTime();
      if (ta !== tb) return ta - tb;
      return a.id - b.id;
    });
  if (thread.length === 0) return false;
  const last = thread[thread.length - 1]!;
  if (last.role === "USER") return true;
  if (last.role === "ASSISTANT" && last.status === "ERROR") return true;
  return false;
}

function buildContactsFromMessages(
  messages: ChatMessageItem[],
  botEnabledByPhone: Record<string, boolean>,
): MonitorContact[] {
  const agg = new Map<
    string,
    { lastAtMs: number; lastIso: string; preview: string }
  >();

  for (const m of messages) {
    const t = new Date(m.timestamp).getTime();
    const cur = agg.get(m.phoneNumber);
    const preview =
      m.content.length > 48 ? `${m.content.slice(0, 48)}…` : m.content;
    if (!cur || t >= cur.lastAtMs) {
      agg.set(m.phoneNumber, { lastAtMs: t, lastIso: m.timestamp, preview });
    }
  }

  return [...agg.entries()]
    .map(([phoneNumber, v]) => {
      const formatted = formatPhoneDisplay(phoneNumber);
      const name = latestContactNameForPhone(messages, phoneNumber);
      const botEnabled = botEnabledByPhone[phoneNumber] !== false;
      return {
        phoneNumber,
        displayPhone: formatted,
        displayTitle: name ?? formatted,
        lastPreview: v.preview,
        lastMessageAt: v.lastIso,
        botEnabled,
      };
    })
    .sort(
      (a, b) =>
        new Date(b.lastMessageAt).getTime() -
        new Date(a.lastMessageAt).getTime(),
    );
}

function threadForPhone(
  messages: ChatMessageItem[],
  phoneNumber: string | null,
): UiThreadMessage[] {
  if (!phoneNumber) return [];
  return messages
    .filter((m) => m.phoneNumber === phoneNumber)
    .sort(
      (a, b) =>
        new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime(),
    )
    .map((m) => ({
      id: String(m.id),
      role: m.role === "USER" ? "user" : "assistant",
      text: m.content,
      sentAt: m.timestamp,
      status: m.status,
    }));
}

function formatPhoneDisplay(raw: string): string {
  const t = raw.trim();
  if (!t) return "—";
  const digits = t.replace(/\D/g, "");
  if (digits.length >= 12 && digits.startsWith("55")) {
    const d = digits.slice(2);
    if (d.length === 11) {
      return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
    }
    if (d.length === 10) {
      return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
    }
  }
  if (digits.length >= 10) {
    return digits;
  }
  return t;
}

function MonitoramentoConversasPageContent() {
  const t = useTranslations("monitor");
  const tApi = useTranslations("api");
  const translateApi = React.useCallback((key: string) => tApi(key), [tApi]);
  const locale = useLocale();
  const dateLocale = toBcp47ForDates(locale);

  const formatTime = (iso: string) => {
    try {
      return new Date(iso).toLocaleTimeString(dateLocale, {
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch {
      return "—";
    }
  };

  const formatAppointmentLine = (iso: string) => {
    try {
      const d = new Date(iso);
      return d.toLocaleString(dateLocale, {
        day: "2-digit",
        month: "short",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch {
      return "—";
    }
  };

  const formatRelativeShort = (iso: string) => {
    try {
      const d = new Date(iso);
      const now = new Date();
      const diffMin = Math.floor((now.getTime() - d.getTime()) / 60_000);
      if (diffMin < 1) return t("relativeNow");
      if (diffMin < 60) return t("relativeMin", { n: diffMin });
      const diffH = Math.floor(diffMin / 60);
      if (diffH < 24) return t("relativeHours", { n: diffH });
      return d.toLocaleDateString(dateLocale, { day: "2-digit", month: "short" });
    } catch {
      return "";
    }
  };

  const searchParams = useSearchParams();
  const scrollRef = React.useRef<HTMLDivElement>(null);
  const [tenantId, setTenantId] = React.useState("");
  const [rawMessages, setRawMessages] = React.useState<ChatMessageItem[]>([]);
  const [botEnabledByPhone, setBotEnabledByPhone] = React.useState<
    Record<string, boolean>
  >({});
  const [selectedPhone, setSelectedPhone] = React.useState<string | null>(null);
  const [contactFilter, setContactFilter] = React.useState<"all" | "pending">("all");
  const [retryingId, setRetryingId] = React.useState<string | null>(null);
  const [lastSyncedAt, setLastSyncedAt] = React.useState<Date | null>(null);
  const [isRefreshing, setIsRefreshing] = React.useState(false);
  const [loadError, setLoadError] = React.useState<string | null>(null);
  const [hasSyncedOnce, setHasSyncedOnce] = React.useState(false);
  const [conversationActionPhone, setConversationActionPhone] = React.useState<
    string | null
  >(null);
  const [humanDraft, setHumanDraft] = React.useState("");
  const [sendingHuman, setSendingHuman] = React.useState(false);
  const [appointmentByDigits, setAppointmentByDigits] = React.useState<
    Record<string, { startsAt: string }>
  >({});
  const [fichaOpen, setFichaOpen] = React.useState(false);
  const [fichaConversationId, setFichaConversationId] = React.useState<string | null>(null);
  const [fichaTitle, setFichaTitle] = React.useState<string | undefined>(undefined);

  const readTenantFromStorage = React.useCallback(() => {
    try {
      setTenantId(localStorage.getItem(TENANT_STORAGE_KEY) ?? "");
    } catch {
      /* ignore */
    }
  }, []);

  React.useEffect(() => {
    readTenantFromStorage();
  }, [readTenantFromStorage]);

  React.useEffect(() => {
    window.addEventListener("focus", readTenantFromStorage);
    return () => window.removeEventListener("focus", readTenantFromStorage);
  }, [readTenantFromStorage]);

  React.useEffect(() => {
    if (!tenantId.trim()) {
      setSelectedPhone(null);
    }
  }, [tenantId]);

  const contacts = React.useMemo(
    () => buildContactsFromMessages(rawMessages, botEnabledByPhone),
    [rawMessages, botEnabledByPhone],
  );

  React.useEffect(() => {
    const raw = searchParams.get("phone");
    if (!raw || !tenantId.trim() || contacts.length === 0) return;
    const digits = raw.replace(/\D/g, "");
    if (!digits) return;
    const match = contacts.find((c) => c.phoneNumber.replace(/\D/g, "") === digits);
    if (match) setSelectedPhone(match.phoneNumber);
  }, [searchParams, contacts, tenantId]);

  const pendingCount = React.useMemo(
    () =>
      contacts.filter((c) =>
        isConversationPending(rawMessages, c.phoneNumber),
      ).length,
    [contacts, rawMessages],
  );

  const phoneDigitsKey = React.useMemo(() => {
    const s = new Set<string>();
    for (const m of rawMessages) {
      const d = m.phoneNumber.replace(/\D/g, "");
      if (d) s.add(d);
    }
    return [...s].sort().join(",");
  }, [rawMessages]);

  React.useEffect(() => {
    const tid = tenantId.trim();
    if (!tid || !hasSyncedOnce) {
      setAppointmentByDigits({});
      return;
    }
    const digits = phoneDigitsKey.split(",").filter(Boolean);
    if (digits.length === 0) {
      setAppointmentByDigits({});
      return;
    }
    let cancelled = false;
    void (async () => {
      try {
        const merged: Record<string, { startsAt: string }> = {};
        for (let i = 0; i < digits.length; i += APPOINTMENT_LOOKUP_CHUNK) {
          const chunk = digits.slice(i, i + APPOINTMENT_LOOKUP_CHUNK);
          const res = await lookupUpcomingAppointmentsByPhones(tid, chunk);
          for (const [k, v] of Object.entries(res.byPhoneDigits)) {
            merged[k] = { startsAt: v.startsAt };
          }
        }
        if (!cancelled) {
          setAppointmentByDigits(merged);
        }
      } catch {
        if (!cancelled) {
          setAppointmentByDigits({});
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [tenantId, phoneDigitsKey, hasSyncedOnce]);

  const filteredContacts = React.useMemo(() => {
    if (contactFilter === "pending") {
      return contacts.filter((c) =>
        isConversationPending(rawMessages, c.phoneNumber),
      );
    }
    return contacts;
  }, [contactFilter, contacts, rawMessages]);

  const messages = React.useMemo(
    () => threadForPhone(rawMessages, selectedPhone),
    [rawMessages, selectedPhone],
  );

  const humanChatEnabled = Boolean(
    tenantId.trim() &&
      selectedPhone &&
      botEnabledByPhone[selectedPhone] === false,
  );

  React.useEffect(() => {
    setHumanDraft("");
  }, [selectedPhone]);

  const refresh = React.useCallback(async () => {
    const tid = tenantId.trim();
    if (!tid) {
      setRawMessages([]);
      setBotEnabledByPhone({});
      setLastSyncedAt(null);
      setLoadError(null);
      setHasSyncedOnce(false);
      return;
    }
    setIsRefreshing(true);
    setLoadError(null);
    try {
      const payload = await getChatMessages(tid);
      setRawMessages(payload.messages);
      setBotEnabledByPhone(payload.botEnabledByPhone);
      setLastSyncedAt(new Date());
    } catch (e: unknown) {
      const msg = toUserFacingApiError(e, translateApi);
      setLoadError(msg);
      toast.error(msg);
    } finally {
      setIsRefreshing(false);
      setHasSyncedOnce(true);
    }
  }, [tenantId, translateApi]);

  const handleRetryAssistant = React.useCallback(
    async (messageId: number) => {
      const tid = tenantId.trim();
      if (!tid) return;
      setRetryingId(String(messageId));
      try {
        await retryChatMessage(tid, messageId);
        toast.success(t("retryStarted"));
        await refresh();
      } catch (e: unknown) {
        toast.error(toUserFacingApiError(e, translateApi));
      } finally {
        setRetryingId(null);
      }
    },
    [tenantId, refresh, translateApi, t],
  );

  const handleHumanHandoff = React.useCallback(
    async (phoneNumber: string) => {
      const tid = tenantId.trim();
      if (!tid) return;
      setConversationActionPhone(phoneNumber);
      try {
        await humanHandoffConversation(tid, phoneNumber);
        toast.success(t("handoffSuccess"));
        await refresh();
      } catch (e: unknown) {
        toast.error(toUserFacingApiError(e, translateApi));
      } finally {
        setConversationActionPhone(null);
      }
    },
    [tenantId, refresh, translateApi, t],
  );

  const handleEnableBot = React.useCallback(
    async (phoneNumber: string) => {
      const tid = tenantId.trim();
      if (!tid) return;
      setConversationActionPhone(phoneNumber);
      try {
        await enableBotForConversation(tid, phoneNumber);
        toast.success(t("enableBotSuccess"));
        await refresh();
      } catch (e: unknown) {
        toast.error(toUserFacingApiError(e, translateApi));
      } finally {
        setConversationActionPhone(null);
      }
    },
    [tenantId, refresh, translateApi, t],
  );

  const handleSendHumanMessage = React.useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      const tid = tenantId.trim();
      if (!tid || !selectedPhone || !humanChatEnabled) return;
      const body = humanDraft.trim();
      if (!body) {
        toast.error(t("activeChatNeedMessage"));
        return;
      }
      setSendingHuman(true);
      try {
        await sendHumanMonitorMessage(tid, selectedPhone, body);
        setHumanDraft("");
        toast.success(t("humanReplySuccess"));
        await refresh();
      } catch (err: unknown) {
        toast.error(toUserFacingApiError(err, translateApi));
      } finally {
        setSendingHuman(false);
      }
    },
    [
      tenantId,
      selectedPhone,
      humanChatEnabled,
      humanDraft,
      refresh,
      translateApi,
      t,
    ],
  );

  React.useEffect(() => {
    void refresh();
  }, [refresh]);

  React.useEffect(() => {
    const interval = setInterval(() => {
      void refresh();
    }, POLL_MS);
    return () => clearInterval(interval);
  }, [refresh]);

  React.useEffect(() => {
    if (selectedPhone === null && filteredContacts.length > 0) {
      setSelectedPhone(filteredContacts[0].phoneNumber);
    }
  }, [filteredContacts, selectedPhone]);

  React.useEffect(() => {
    if (
      selectedPhone != null &&
      filteredContacts.length > 0 &&
      !filteredContacts.some((c) => c.phoneNumber === selectedPhone)
    ) {
      setSelectedPhone(filteredContacts[0].phoneNumber);
    }
  }, [filteredContacts, selectedPhone]);

  React.useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages, selectedPhone]);

  const selectedWaConversationId = React.useMemo(() => {
    if (!selectedPhone) return null;
    const digits = selectedPhone.replace(/\D/g, "");
    return digits ? `wa-${digits}` : null;
  }, [selectedPhone]);

  return (
    <div className="flex h-[calc(100dvh-3.5rem-2rem)] min-h-[420px] flex-col gap-4">
      <CustomerRecordDialog
        open={fichaOpen}
        onOpenChange={setFichaOpen}
        tenantId={tenantId}
        conversationId={fichaConversationId}
        titleFallback={fichaTitle}
      />
      <div className="shrink-0">
        <h1 className="text-2xl font-semibold tracking-tight">{t("title")}</h1>
        <p className="text-muted-foreground">
          {t("subtitle", { seconds: POLL_MS / 1000 })}
        </p>
        <div className="mt-3 max-w-md space-y-1">
          <Label className="text-muted-foreground">{t("accountId")}</Label>
          <p
            className={cn(
              "min-h-9 font-mono text-base font-semibold tracking-tight text-foreground sm:text-lg",
              !tenantId.trim() && "font-normal text-muted-foreground",
            )}
            aria-label={t("accountId")}
          >
            {tenantId.trim() || "—"}
          </p>
          {!tenantId.trim() ? (
            <p className="pt-1 text-xs text-amber-600 dark:text-amber-400/90">
              {t("needAccountWarning")}
            </p>
          ) : null}
          {loadError ? (
            <p className="pt-1 text-xs text-destructive">{loadError}</p>
          ) : null}
        </div>
      </div>

      <div className="flex min-h-0 flex-1 flex-col gap-0 overflow-hidden rounded-2xl border border-border/80 bg-card/40 shadow-lg ring-1 ring-black/5 dark:ring-white/10 md:flex-row md:gap-0">
        <aside className="flex h-auto max-h-[42vh] min-h-[200px] w-full shrink-0 flex-col overflow-hidden border-b border-border/60 bg-sidebar/80 md:max-h-none md:w-full md:max-w-[280px] md:border-b-0 md:border-r">
          <div className="border-b border-border/60 px-3 py-3">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              {t("contacts")}
            </p>
            <p className="text-[11px] text-muted-foreground">
              {tenantId.trim()
                ? t("summaryLine", {
                    filtered: filteredContacts.length,
                    total: contacts.length,
                  })
                : "—"}
            </p>
          </div>
          <div className="border-b border-border/60 px-2 py-2">
            <div
              className="flex rounded-lg bg-muted/50 p-0.5 dark:bg-muted/30"
              role="tablist"
              aria-label={t("filterAria")}
            >
              <button
                type="button"
                role="tab"
                aria-selected={contactFilter === "all"}
                onClick={() => setContactFilter("all")}
                className={cn(
                  "min-h-11 flex-1 touch-manipulation rounded-md px-2 py-2 text-center text-[11px] font-medium transition-colors sm:min-h-0 sm:py-1.5",
                  contactFilter === "all"
                    ? "bg-background text-foreground shadow-sm dark:bg-card"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                {t("filterAll")}
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={contactFilter === "pending"}
                onClick={() => setContactFilter("pending")}
                className={cn(
                  "flex min-h-11 flex-1 touch-manipulation items-center justify-center gap-1 rounded-md px-2 py-2 text-center text-[11px] font-medium transition-colors sm:min-h-0 sm:py-1.5",
                  contactFilter === "pending"
                    ? "bg-background text-foreground shadow-sm dark:bg-card"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                {t("filterPending")}
                {pendingCount > 0 ? (
                  <span
                    className="min-w-[1.125rem] rounded-full bg-amber-500/90 px-1 text-[9px] font-semibold tabular-nums text-white dark:bg-amber-600"
                    aria-label={t("pendingBadgeAria", { count: pendingCount })}
                  >
                    {pendingCount > 99 ? "99+" : pendingCount}
                  </span>
                ) : (
                  <span className="rounded-full bg-muted-foreground/25 px-1 text-[9px] tabular-nums text-muted-foreground dark:bg-muted-foreground/20">
                    0
                  </span>
                )}
              </button>
            </div>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto p-2">
            {!tenantId.trim() ? (
              <p className="px-2 py-4 text-center text-xs text-muted-foreground">
                {t("configureAccount")}
              </p>
            ) : !hasSyncedOnce && isRefreshing ? (
              <div className="space-y-2 px-2 py-4" aria-busy="true">
                {[1, 2, 3].map((i) => (
                  <div
                    key={i}
                    className="h-14 animate-pulse rounded-xl bg-muted/50 dark:bg-muted/25"
                  />
                ))}
              </div>
            ) : contacts.length === 0 && !isRefreshing ? (
              <p className="px-2 py-4 text-center text-xs text-muted-foreground">
                {t("noConversations")}
              </p>
            ) : filteredContacts.length === 0 && !isRefreshing ? (
              <p className="px-2 py-4 text-center text-xs text-muted-foreground">
                {contactFilter === "pending"
                  ? t("noPending")
                  : t("noConversations")}
              </p>
            ) : filteredContacts.length === 0 && isRefreshing ? (
              <p className="px-2 py-4 text-center text-xs text-muted-foreground">
                {t("loadingShort")}
              </p>
            ) : (
              <ul className="space-y-1">
                {filteredContacts.map((c) => {
                  const active = c.phoneNumber === selectedPhone;
                  const actionBusy = conversationActionPhone === c.phoneNumber;
                  const digits = c.phoneNumber.replace(/\D/g, "");
                  const upcomingAppt = digits ? appointmentByDigits[digits] : undefined;
                  return (
                    <li key={c.phoneNumber}>
                      <div
                        className={cn(
                          "overflow-hidden rounded-xl transition-colors",
                          active
                            ? "bg-primary/15 ring-1 ring-primary/30"
                            : "bg-transparent",
                          !c.botEnabled &&
                            "ring-2 ring-amber-400/90 ring-offset-2 ring-offset-background dark:ring-amber-500/70",
                          !c.botEnabled && !active && "bg-amber-500/[0.07]",
                        )}
                      >
                        <button
                          type="button"
                          onClick={() => setSelectedPhone(c.phoneNumber)}
                          className={cn(
                            "flex min-h-11 w-full touch-manipulation flex-col px-3 py-3 text-left text-sm transition-colors",
                            active
                              ? "text-foreground"
                              : "text-muted-foreground hover:bg-accent/50 hover:text-foreground",
                          )}
                        >
                          <span className="flex items-center gap-1.5 font-medium">
                            {!c.botEnabled ? (
                              <Hand
                                className="h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400"
                                aria-hidden
                              />
                            ) : null}
                            {c.displayTitle}
                          </span>
                          {c.displayTitle !== c.displayPhone ? (
                            <span className="block text-[11px] tabular-nums text-muted-foreground/90">
                              {c.displayPhone}
                            </span>
                          ) : null}
                          {upcomingAppt ? (
                            <span className="mt-1 block text-[10px] font-medium leading-tight text-emerald-700 dark:text-emerald-400/95">
                              {t("appointmentBadge", {
                                date: formatAppointmentLine(upcomingAppt.startsAt),
                              })}
                            </span>
                          ) : null}
                          <span className="mt-0.5 line-clamp-2 text-[11px] leading-snug text-muted-foreground">
                            {c.lastPreview}
                          </span>
                          <span className="mt-1 text-[10px] text-muted-foreground/80">
                            {formatRelativeShort(c.lastMessageAt)}
                          </span>
                        </button>
                        <div
                          className="flex items-center justify-between gap-2 border-t border-border/50 px-2 py-2"
                          onClick={(e) => e.stopPropagation()}
                          onKeyDown={(e) => e.stopPropagation()}
                        >
                          <span className="min-w-0 flex-1 text-[11px] leading-tight text-muted-foreground">
                            {t("assumeConversationToggle")}
                          </span>
                          <button
                            type="button"
                            role="switch"
                            aria-checked={!c.botEnabled}
                            aria-label={t("assumeConversationToggle")}
                            disabled={!tenantId.trim() || actionBusy}
                            className={cn(
                              "relative inline-flex h-7 w-12 shrink-0 cursor-pointer rounded-full border transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50",
                              !c.botEnabled
                                ? "border-amber-600 bg-amber-500 dark:border-amber-500"
                                : "border-border bg-muted",
                            )}
                            onClick={() => {
                              if (actionBusy) return;
                              if (c.botEnabled) void handleHumanHandoff(c.phoneNumber);
                              else void handleEnableBot(c.phoneNumber);
                            }}
                          >
                            <span
                              className={cn(
                                "pointer-events-none absolute top-0.5 left-0.5 block h-6 w-6 rounded-full bg-white shadow-sm ring-1 ring-black/10 transition-all duration-200 dark:bg-background dark:ring-white/10",
                                !c.botEnabled && "left-[calc(100%-1.625rem)]",
                              )}
                            />
                          </button>
                        </div>
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </aside>

        <section className="flex min-h-0 min-w-0 flex-1 flex-col md:min-h-0">
          <div
            className={cn(
              "flex min-h-[52px] shrink-0 items-center justify-between border-b border-border/60 bg-muted/20 px-3 py-3 sm:px-4",
              selectedPhone &&
                contacts.find((x) => x.phoneNumber === selectedPhone)
                  ?.botEnabled === false &&
                "border-amber-500/40 bg-amber-500/[0.06]",
            )}
          >
            <div>
              <p className="flex flex-wrap items-center gap-2 text-sm font-medium">
                {selectedPhone &&
                contacts.find((x) => x.phoneNumber === selectedPhone)
                  ?.botEnabled === false ? (
                  <Hand
                    className="h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400"
                    aria-hidden
                  />
                ) : null}
                {selectedPhone && selectedWaConversationId ? (
                  <button
                    type="button"
                    className="text-left text-primary underline-offset-4 hover:underline"
                    onClick={() => {
                      const c = contacts.find((x) => x.phoneNumber === selectedPhone);
                      setFichaConversationId(selectedWaConversationId);
                      setFichaTitle(c?.displayTitle ?? undefined);
                      setFichaOpen(true);
                    }}
                  >
                    {(() => {
                      const c = contacts.find(
                        (x) => x.phoneNumber === selectedPhone,
                      );
                      return c?.displayTitle ?? formatPhoneDisplay(selectedPhone);
                    })()}
                  </button>
                ) : selectedPhone ? (
                  (() => {
                    const c = contacts.find(
                      (x) => x.phoneNumber === selectedPhone,
                    );
                    return c?.displayTitle ?? formatPhoneDisplay(selectedPhone);
                  })()
                ) : (
                  t("selectContactHeader")
                )}
              </p>
              {selectedPhone &&
              contacts.some(
                (x) =>
                  x.phoneNumber === selectedPhone &&
                  x.displayTitle !== x.displayPhone,
              ) ? (
                <p className="text-xs text-muted-foreground tabular-nums">
                  {formatPhoneDisplay(selectedPhone)}
                </p>
              ) : null}
              <p className="flex items-center gap-2 text-xs text-muted-foreground">
                <RefreshCw
                  className={cn(
                    "h-3.5 w-3.5",
                    isRefreshing ? "animate-spin" : "",
                  )}
                  aria-hidden
                />
                {!tenantId.trim()
                  ? "—"
                  : lastSyncedAt
                    ? t("lastSync", {
                        time: lastSyncedAt.toLocaleTimeString(dateLocale),
                      })
                    : t("syncing")}
              </p>
            </div>
          </div>

          <div
            ref={scrollRef}
            className="min-h-0 flex-1 space-y-4 overflow-y-auto bg-background/30 p-4"
          >
            {!tenantId.trim() ? (
              <p className="py-16 text-center text-sm text-muted-foreground">
                {t("needAccountBody")}
              </p>
            ) : !hasSyncedOnce && isRefreshing ? (
              <p className="py-16 text-center text-sm text-muted-foreground">
                {t("loadingMessages")}
              </p>
            ) : !selectedPhone ? (
              <p className="py-16 text-center text-sm text-muted-foreground">
                {t("chooseContact")}
              </p>
            ) : messages.length === 0 ? (
              <p className="py-16 text-center text-sm text-muted-foreground">
                {t("noMessagesThread")}
              </p>
            ) : (
              messages.map((m) => (
                <ChatBubble
                  key={m.id}
                  role={m.role}
                  timeLabel={formatTime(m.sentAt)}
                  layout="monitor"
                  deliveryStatus={
                    m.role === "assistant" ? m.status : undefined
                  }
                  onRetry={
                    m.role === "assistant" && m.status === "ERROR"
                      ? () =>
                          void handleRetryAssistant(Number.parseInt(m.id, 10))
                      : undefined
                  }
                  retryDisabled={retryingId === m.id}
                >
                  {m.text}
                </ChatBubble>
              ))
            )}
          </div>

          <form
            className="shrink-0 border-t border-border/60 bg-muted/10 px-3 py-3 sm:px-4"
            onSubmit={handleSendHumanMessage}
          >
            {tenantId.trim() && selectedPhone && !humanChatEnabled ? (
              <p className="mb-2 text-[11px] text-muted-foreground">
                {t("activeChatDisabledHint")}
              </p>
            ) : null}
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
              <Input
                value={humanDraft}
                onChange={(ev) => setHumanDraft(ev.target.value)}
                placeholder={t("activeChatPlaceholder")}
                disabled={!humanChatEnabled || sendingHuman}
                maxLength={4096}
                className="min-w-0 flex-1"
                autoComplete="off"
                aria-label={t("activeChatPlaceholder")}
              />
              <Button
                type="submit"
                disabled={!humanChatEnabled || sendingHuman}
                className="shrink-0 sm:min-w-[7rem]"
              >
                {sendingHuman ? t("actionWorking") : t("activeChatSend")}
              </Button>
            </div>
          </form>
        </section>
      </div>
    </div>
  );
}

export default function MonitoramentoConversasPage() {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-[50vh] items-center justify-center p-8">
          <RefreshCw
            className="h-8 w-8 animate-spin text-muted-foreground"
            aria-hidden
          />
        </div>
      }
    >
      <MonitoramentoConversasPageContent />
    </Suspense>
  );
}
