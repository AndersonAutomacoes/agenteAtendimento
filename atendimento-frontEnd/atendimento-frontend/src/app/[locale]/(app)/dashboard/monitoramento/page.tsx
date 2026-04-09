"use client";

import { RefreshCw } from "lucide-react";
import * as React from "react";
import { useLocale, useTranslations } from "next-intl";
import { toast } from "sonner";

import { ChatBubble } from "@/components/chat/chat-bubble";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toBcp47ForDates } from "@/lib/intl-locale";
import { cn } from "@/lib/utils";
import {
  getChatMessages,
  retryChatMessage,
  toUserFacingApiError,
  type ChatMessageItem,
} from "@/services/apiService";

const TENANT_STORAGE_KEY = "cerebro-tenant-id";
const POLL_MS = 5_000;

type MonitorContact = {
  phoneNumber: string;
  displayPhone: string;
  displayTitle: string;
  lastPreview: string;
  lastMessageAt: string;
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
      return {
        phoneNumber,
        displayPhone: formatted,
        displayTitle: name ?? formatted,
        lastPreview: v.preview,
        lastMessageAt: v.lastIso,
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

export default function MonitoramentoConversasPage() {
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

  const scrollRef = React.useRef<HTMLDivElement>(null);
  const [tenantId, setTenantId] = React.useState("");
  const [rawMessages, setRawMessages] = React.useState<ChatMessageItem[]>([]);
  const [selectedPhone, setSelectedPhone] = React.useState<string | null>(null);
  const [contactFilter, setContactFilter] = React.useState<"all" | "pending">("all");
  const [retryingId, setRetryingId] = React.useState<string | null>(null);
  const [lastSyncedAt, setLastSyncedAt] = React.useState<Date | null>(null);
  const [isRefreshing, setIsRefreshing] = React.useState(false);
  const [loadError, setLoadError] = React.useState<string | null>(null);
  const [hasSyncedOnce, setHasSyncedOnce] = React.useState(false);

  React.useEffect(() => {
    try {
      const v = localStorage.getItem(TENANT_STORAGE_KEY);
      if (v) setTenantId(v);
    } catch {
      /* ignore */
    }
  }, []);

  React.useEffect(() => {
    if (!tenantId.trim()) {
      setSelectedPhone(null);
    }
  }, [tenantId]);

  const persistTenant = (value: string) => {
    setTenantId(value);
    try {
      localStorage.setItem(TENANT_STORAGE_KEY, value);
    } catch {
      /* ignore */
    }
  };

  const contacts = React.useMemo(
    () => buildContactsFromMessages(rawMessages),
    [rawMessages],
  );

  const pendingCount = React.useMemo(
    () =>
      contacts.filter((c) =>
        isConversationPending(rawMessages, c.phoneNumber),
      ).length,
    [contacts, rawMessages],
  );

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

  const refresh = React.useCallback(async () => {
    const tid = tenantId.trim();
    if (!tid) {
      setRawMessages([]);
      setLastSyncedAt(null);
      setLoadError(null);
      setHasSyncedOnce(false);
      return;
    }
    setIsRefreshing(true);
    setLoadError(null);
    try {
      const list = await getChatMessages(tid);
      setRawMessages(list);
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

  return (
    <div className="flex h-[calc(100dvh-3.5rem-3rem)] min-h-[420px] flex-col gap-4">
      <div className="shrink-0">
        <h1 className="text-2xl font-semibold tracking-tight">{t("title")}</h1>
        <p className="text-muted-foreground">
          {t("subtitle", { seconds: POLL_MS / 1000 })}
        </p>
        <div className="mt-3 max-w-md space-y-2">
          <Label htmlFor="monitor-tenant">{t("accountId")}</Label>
          <Input
            id="monitor-tenant"
            placeholder={t("placeholderTenant")}
            value={tenantId}
            onChange={(e) => persistTenant(e.target.value)}
            autoComplete="off"
            className="rounded-xl font-mono text-sm"
          />
          {!tenantId.trim() ? (
            <p className="text-xs text-amber-600 dark:text-amber-400/90">
              {t("needAccountWarning")}
            </p>
          ) : null}
          {loadError ? (
            <p className="text-xs text-destructive">{loadError}</p>
          ) : null}
        </div>
      </div>

      <div className="flex min-h-0 flex-1 gap-4 overflow-hidden rounded-2xl border border-border/80 bg-card/40 shadow-lg ring-1 ring-black/5 dark:ring-white/10">
        <aside className="flex w-full max-w-[280px] shrink-0 flex-col border-r border-border/60 bg-sidebar/80">
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
                  "flex-1 rounded-md px-2 py-1.5 text-center text-[11px] font-medium transition-colors",
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
                  "flex flex-1 items-center justify-center gap-1 rounded-md px-2 py-1.5 text-center text-[11px] font-medium transition-colors",
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
                  return (
                    <li key={c.phoneNumber}>
                      <button
                        type="button"
                        onClick={() => setSelectedPhone(c.phoneNumber)}
                        className={cn(
                          "flex w-full flex-col rounded-xl px-3 py-2.5 text-left text-sm transition-colors",
                          active
                            ? "bg-primary/15 text-foreground ring-1 ring-primary/30"
                            : "text-muted-foreground hover:bg-accent/50 hover:text-foreground",
                        )}
                      >
                        <span className="font-medium">{c.displayTitle}</span>
                        {c.displayTitle !== c.displayPhone ? (
                          <span className="block text-[11px] tabular-nums text-muted-foreground/90">
                            {c.displayPhone}
                          </span>
                        ) : null}
                        <span className="mt-0.5 line-clamp-2 text-[11px] leading-snug text-muted-foreground">
                          {c.lastPreview}
                        </span>
                        <span className="mt-1 text-[10px] text-muted-foreground/80">
                          {formatRelativeShort(c.lastMessageAt)}
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </aside>

        <section className="flex min-h-0 min-w-0 flex-1 flex-col">
          <div className="flex shrink-0 items-center justify-between border-b border-border/60 bg-muted/20 px-4 py-3">
            <div>
              <p className="text-sm font-medium">
                {selectedPhone
                  ? (() => {
                      const c = contacts.find(
                        (x) => x.phoneNumber === selectedPhone,
                      );
                      return c?.displayTitle ?? formatPhoneDisplay(selectedPhone);
                    })()
                  : t("selectContactHeader")}
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
        </section>
      </div>
    </div>
  );
}
