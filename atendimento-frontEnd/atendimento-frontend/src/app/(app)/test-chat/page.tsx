"use client";

import { Send } from "lucide-react";
import * as React from "react";
import { toast } from "sonner";

import { ChatBubble } from "@/components/chat/chat-bubble";
import { ChatSkeleton } from "@/components/chat/chat-skeleton";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { postChat } from "@/services/apiService";

const TENANT_STORAGE_KEY = "cerebro-tenant-id";

type ChatLine = {
  id: string;
  role: "user" | "assistant";
  text: string;
  time: Date;
};

function formatTime(d: Date) {
  return d.toLocaleTimeString("pt-BR", {
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function TestChatPage() {
  /** Estado definido só no cliente (useEffect) para o SSR e a 1.ª hidratação coincidirem. */
  const [sessionId, setSessionId] = React.useState("");
  const scrollRef = React.useRef<HTMLDivElement>(null);
  const [tenantId, setTenantId] = React.useState("");
  const [draft, setDraft] = React.useState("");
  const [messages, setMessages] = React.useState<ChatLine[]>([]);
  const [awaitingReply, setAwaitingReply] = React.useState(false);

  React.useEffect(() => {
    setSessionId(crypto.randomUUID());
  }, []);

  React.useEffect(() => {
    try {
      const v = localStorage.getItem(TENANT_STORAGE_KEY);
      if (v) setTenantId(v);
    } catch {
      /* ignore */
    }
  }, []);

  React.useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages, awaitingReply]);

  const persistTenant = (value: string) => {
    setTenantId(value);
    try {
      localStorage.setItem(TENANT_STORAGE_KEY, value);
    } catch {
      /* ignore */
    }
  };

  const send = async () => {
    const tid = tenantId.trim();
    const msg = draft.trim();
    if (!tid) {
      toast.error("Indique o tenantId.");
      return;
    }
    if (!msg) {
      toast.error("Escreva uma mensagem.");
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

    const dismiss = toast.loading("A gerar resposta…");
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
      toast.success("Resposta recebida.", { id: dismiss });
    } catch (e) {
      const err = e instanceof Error ? e.message : "Pedido falhou";
      toast.error(err, { id: dismiss });
      const failLine: ChatLine = {
        id: `e-${Date.now()}`,
        role: "assistant",
        text: `Não foi possível obter resposta: ${err}`,
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
        <h1 className="text-2xl font-semibold tracking-tight">Chat de teste</h1>
        <p className="text-muted-foreground">
          Ambiente tipo conversa: a sua mensagem à direita, a IA à esquerda.
        </p>
        <p className="mt-1 font-mono text-[11px] text-muted-foreground">
          sessionId: {sessionId || "—"}
        </p>
      </div>

      <div className="space-y-2">
        <Label htmlFor="chat-tenant">Tenant ID</Label>
        <Input
          id="chat-tenant"
          placeholder="ex.: tenant-demo"
          value={tenantId}
          onChange={(e) => persistTenant(e.target.value)}
          autoComplete="off"
          className="rounded-xl"
        />
      </div>

      <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-2xl border border-border/80 bg-card/50 shadow-lg ring-1 ring-black/5 dark:ring-white/10">
        <div className="border-b border-border/60 bg-muted/30 px-4 py-3">
          <p className="text-sm font-medium">Conversa</p>
          <p className="text-xs text-muted-foreground">
            Mensagens desta sessão (até recarregar a página)
          </p>
        </div>

        <div
          ref={scrollRef}
          className="min-h-[min(420px,calc(100dvh-22rem))] space-y-4 overflow-y-auto bg-background/40 p-4"
        >
          {messages.length === 0 && !awaitingReply ? (
            <p className="py-8 text-center text-sm text-muted-foreground">
              Comece por enviar uma pergunta abaixo.
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
            Mensagem
          </Label>
          <Textarea
            id="chat-msg"
            placeholder="Escreva a sua dúvida…"
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
              className="rounded-xl shadow-md"
              disabled={awaitingReply}
              onClick={() => void send()}
            >
              <Send className="mr-2 h-4 w-4" />
              Enviar
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
