"use client";

import { Loader2 } from "lucide-react";
import * as React from "react";
import { useLocale, useTranslations } from "next-intl";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { toBcp47ForDates } from "@/lib/intl-locale";
import {
  getCrmSummary,
  patchCrmCustomerNotes,
  toUserFacingApiError,
  type CrmSummaryResponse,
} from "@/services/apiService";

type CustomerRecordDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  tenantId: string;
  conversationId: string | null;
  titleFallback?: string;
};

export function CustomerRecordDialog({
  open,
  onOpenChange,
  tenantId,
  conversationId,
  titleFallback,
}: CustomerRecordDialogProps) {
  const t = useTranslations("crm");
  const tApi = useTranslations("api");
  const translateApi = React.useCallback((key: string) => tApi(key), [tApi]);
  const locale = toBcp47ForDates(useLocale());

  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [data, setData] = React.useState<CrmSummaryResponse | null>(null);
  const [notesDraft, setNotesDraft] = React.useState("");
  const [savingNotes, setSavingNotes] = React.useState(false);

  React.useEffect(() => {
    if (!open || !tenantId.trim() || !conversationId?.trim()) {
      setData(null);
      setError(null);
      setNotesDraft("");
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    void (async () => {
      try {
        const res = await getCrmSummary(tenantId.trim(), conversationId.trim());
        if (!cancelled) {
          setData(res);
          setNotesDraft(res.customer?.internalNotes ?? "");
        }
      } catch (e: unknown) {
        if (!cancelled) {
          setData(null);
          setError(toUserFacingApiError(e, translateApi));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [open, tenantId, conversationId, translateApi]);

  const formatDt = (iso: string) => {
    try {
      return new Date(iso).toLocaleString(locale, {
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

  const handleSaveNotes = async () => {
    const cid = data?.customer?.id;
    if (!cid || !tenantId.trim()) return;
    setSavingNotes(true);
    try {
      await patchCrmCustomerNotes(tenantId.trim(), cid, notesDraft);
      toast.success(t("notesSaved"));
      setData((prev) =>
        prev && prev.customer
          ? {
              ...prev,
              customer: { ...prev.customer, internalNotes: notesDraft || null },
            }
          : prev,
      );
    } catch (e: unknown) {
      toast.error(toUserFacingApiError(e, translateApi));
    } finally {
      setSavingNotes(false);
    }
  };

  const displayName =
    data?.customer?.fullName?.trim() ||
    titleFallback?.trim() ||
    t("clientNameFallback");

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[min(90vh,720px)] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription className="sr-only">{t("title")}</DialogDescription>
        </DialogHeader>

        {!conversationId?.trim() ? (
          <p className="text-sm text-muted-foreground">{t("noConversationId")}</p>
        ) : loading ? (
          <div className="flex items-center gap-2 py-10 text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" aria-hidden />
            <span>{t("loading")}</span>
          </div>
        ) : error ? (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        ) : (
          <div className="space-y-6 text-sm">
            <section>
              <h3 className="mb-2 font-medium text-foreground">{t("summaryTitle")}</h3>
              <div className="rounded-lg border border-border/80 bg-muted/20 p-3 text-muted-foreground">
                <p className="font-medium text-foreground">{displayName}</p>
                {data?.customer ? (
                  <ul className="mt-2 list-inside list-disc space-y-0.5 text-xs">
                    <li>
                      ID conversa:{" "}
                      <span className="font-mono text-[11px] text-foreground">
                        {data.customer.conversationId}
                      </span>
                    </li>
                    {data.customer.phoneNumber ? (
                      <li>Tel.: {data.customer.phoneNumber}</li>
                    ) : null}
                    <li>Primeiro contacto: {formatDt(data.customer.firstInteraction)}</li>
                    <li>Agendamentos: {data.customer.totalAppointments}</li>
                  </ul>
                ) : (
                  <p className="text-xs text-muted-foreground">{t("noCrmRow")}</p>
                )}
              </div>
            </section>

            {data?.customer?.aiSalesInsight?.trim() ? (
              <section>
                <h3 className="mb-2 font-medium text-foreground">{t("aiInsightTitle")}</h3>
                <p className="rounded-lg border border-cyan-500/25 bg-cyan-500/5 px-3 py-2.5 text-sm leading-relaxed text-foreground/90">
                  {data.customer.aiSalesInsight}
                </p>
              </section>
            ) : data?.customer ? (
              <section>
                <h3 className="mb-2 font-medium text-foreground">{t("aiInsightTitle")}</h3>
                <p className="text-xs text-muted-foreground">{t("aiInsightEmpty")}</p>
              </section>
            ) : null}

            <section>
              <h3 className="mb-2 font-medium text-foreground">{t("messagesTitle")}</h3>
              {!data?.messages.length ? (
                <p className="text-xs text-muted-foreground">{t("messagesEmpty")}</p>
              ) : (
                <ul className="max-h-48 space-y-2 overflow-y-auto rounded-lg border border-border/60 p-2 text-xs">
                  {data.messages.map((m) => (
                    <li
                      key={`${m.id}-${m.occurredAt}`}
                      className="rounded-md bg-muted/30 px-2 py-1.5"
                    >
                      <span className="font-medium text-foreground">{m.role}</span>{" "}
                      <span className="text-muted-foreground">
                        {formatDt(m.occurredAt)}
                      </span>
                      <p className="mt-0.5 whitespace-pre-wrap text-foreground/90">{m.content}</p>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section>
              <h3 className="mb-2 font-medium text-foreground">{t("appointmentsTitle")}</h3>
              {!data?.appointments.length ? (
                <p className="text-xs text-muted-foreground">{t("appointmentsEmpty")}</p>
              ) : (
                <ul className="space-y-2 text-xs">
                  {data.appointments.map((a) => (
                    <li
                      key={a.id}
                      className="flex flex-col gap-0.5 rounded-lg border border-border/60 px-3 py-2"
                    >
                      <span className="font-medium text-foreground">{a.serviceName}</span>
                      <span className="text-muted-foreground">
                        {formatDt(a.startsAt)} · {a.statusLabel}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            {data?.customer ? (
              <section>
                <h3 className="mb-2 font-medium text-foreground">{t("notesTitle")}</h3>
                <Textarea
                  value={notesDraft}
                  onChange={(e) => setNotesDraft(e.target.value)}
                  placeholder={t("notesPlaceholder")}
                  rows={4}
                  className="resize-y text-sm"
                />
                <DialogFooter className="mt-3 sm:justify-start">
                  <Button
                    type="button"
                    size="sm"
                    onClick={() => void handleSaveNotes()}
                    disabled={savingNotes}
                  >
                    {savingNotes ? (
                      <>
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />
                        …
                      </>
                    ) : (
                      t("notesSave")
                    )}
                  </Button>
                </DialogFooter>
              </section>
            ) : null}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
