"use client";

import { Send } from "lucide-react";
import * as React from "react";
import { useLocale, useTranslations } from "next-intl";
import { toast } from "sonner";

import { ChatBubble } from "@/components/chat/chat-bubble";
import { ChatSkeleton } from "@/components/chat/chat-skeleton";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { toBcp47ForDates } from "@/lib/intl-locale";
import { cn } from "@/lib/utils";
import { randomUuid } from "@/lib/random-uuid";
import { postChat, toUserFacingApiError } from "@/services/apiService";

const TENANT_STORAGE_KEY = "cerebro-tenant-id";

type ChatLine = {
  id: string;
  role: "user" | "assistant";
  text: string;
  time: Date;
};

export default function TestChatPage() {
  const t = useTranslations("testChat");
  const tApi = useTranslations("api");
  const translateApi = React.useCallback((key: string) => tApi(key), [tApi]);
  const locale = useLocale();
  const dateLocale = toBcp47ForDates(locale);

  const formatTime = (d: Date) =>
    d.toLocaleTimeString(dateLocale, {
      hour: "2-digit",
      minute: "2-digit",
    });

  const [sessionId, setSessionId] = React.useState("");
  const scrollRef = React.useRef<HTMLDivElement>(null);
  const [tenantId, setTenantId] = React.useState("");
  const [draft, setDraft] = React.useState("");
  const [messages, setMessages] = React.useState<ChatLine[]>([]);
  const [awaitingReply, setAwaitingReply] = React.useState(false);

  React.useEffect(() => {
    setSessionId(randomUuid());
  }, []);

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
    const el = scrollRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages, awaitingReply]);

  const send = async () => {
    const tid = tenantId.trim();
    const msg = draft.trim();
    if (!tid) {
      toast.error(t("toastNeedAccount"));
      return;
    }
    if (!msg) {
      toast.error(t("toastNeedMessage"));
      return;
    }

    const userLine: ChatLine = {
      id: `u-${Date.now()}`,
      role: "user",
      text: msg,
      time: new Date(),
    };
    setMessages((prev) => [...prev, userLine]);
    setDraft("");
    setAwaitingReply(true);

    const sid = sessionId || crypto.randomUUID();
    if (!sessionId) {
      setSessionId(sid);
    }

    const dismiss = toast.loading(t("toastGenerating"));
    try {
      const result = await postChat({
        tenantId: tid,
        sessionId: sid,
        message: msg,
      });
      const assistantLine: ChatLine = {
        id: `a-${Date.now()}`,
        role: "assistant",
        text: result.assistantMessage,
        time: new Date(),
      };
      setMessages((prev) => [...prev, assistantLine]);
      toast.success(t("toastReceived"), { id: dismiss });
    } catch (e) {
      const err = toUserFacingApiError(e, translateApi);
      toast.error(err, { id: dismiss });
      const failLine: ChatLine = {
        id: `e-${Date.now()}`,
        role: "assistant",
        text: t("assistantErrorPrefix", { error: err }),
        time: new Date(),
      };
      setMessages((prev) => [...prev, failLine]);
    } finally {
      setAwaitingReply(false);
    }
  };

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{t("title")}</h1>
        <p className="text-muted-foreground">{t("subtitle")}</p>
        <p className="mt-1 font-mono text-[11px] text-muted-foreground">
          {t("sessionId")} {sessionId || "—"}
        </p>
      </div>

      <div className="space-y-1">
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
      </div>

      <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-2xl border border-border/80 bg-card/50 shadow-lg ring-1 ring-black/5 dark:ring-white/10">
        <div className="border-b border-border/60 bg-muted/30 px-4 py-3">
          <p className="text-sm font-medium">{t("panelTitle")}</p>
          <p className="text-xs text-muted-foreground">{t("panelSubtitle")}</p>
        </div>

        <div
          ref={scrollRef}
          className="min-h-[min(420px,calc(100dvh-22rem))] space-y-4 overflow-y-auto bg-background/40 p-4"
        >
          {messages.length === 0 && !awaitingReply ? (
            <p className="py-8 text-center text-sm text-muted-foreground">
              {t("emptyHint")}
            </p>
          ) : null}
          {messages.map((m) => (
            <ChatBubble
              key={m.id}
              role={m.role}
              timeLabel={formatTime(m.time)}
            >
              {m.text}
            </ChatBubble>
          ))}
          {awaitingReply ? (
            <div className="flex justify-start">
              <ChatSkeleton />
            </div>
          ) : null}
        </div>

        <div className="border-t border-border/60 bg-muted/20 p-4">
          <Label htmlFor="chat-msg" className="sr-only">
            {t("messageLabel")}
          </Label>
          <Textarea
            id="chat-msg"
            placeholder={t("messagePlaceholder")}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            rows={3}
            className="mb-3 resize-none rounded-xl border-border/80 bg-background/80"
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                void send();
              }
            }}
          />
          <div className="flex justify-end">
            <Button
              type="button"
              className="min-h-11 w-full touch-manipulation rounded-xl shadow-md sm:w-auto"
              disabled={awaitingReply}
              onClick={() => void send()}
            >
              <Send className="mr-2 h-4 w-4" />
              {t("send")}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
