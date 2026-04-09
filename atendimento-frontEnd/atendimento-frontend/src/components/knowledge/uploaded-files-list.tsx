"use client";

import * as React from "react";
import { useLocale, useTranslations } from "next-intl";

import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { toBcp47ForDates } from "@/lib/intl-locale";
import { cn } from "@/lib/utils";
import type { KnowledgeBaseFileItem } from "@/services/apiService";

export type KnowledgeBaseUploadingState = {
  fileName: string;
  sizeBytes: number;
} | null;

type KnowledgeBaseFilesTableProps = {
  files: KnowledgeBaseFileItem[];
  loading: boolean;
  accountConfigured: boolean;
  uploading: KnowledgeBaseUploadingState;
  onDeleteRequest: (batchId: string, fileName: string) => void;
  deleteBusyBatchId: string | null;
};

export function KnowledgeBaseFilesTable({
  files,
  loading,
  accountConfigured,
  uploading,
  onDeleteRequest,
  deleteBusyBatchId,
}: KnowledgeBaseFilesTableProps) {
  const t = useTranslations("knowledgeTable");
  const locale = useLocale();
  const dateLocale = toBcp47ForDates(locale);

  const formatFileSize = (bytes: number): string => {
    if (bytes <= 0 || !Number.isFinite(bytes)) return "—";
    const u = ["B", "KB", "MB", "GB"];
    let n = bytes;
    let i = 0;
    while (n >= 1024 && i < u.length - 1) {
      n /= 1024;
      i++;
    }
    return `${n.toLocaleString(dateLocale, { maximumFractionDigits: i === 0 ? 0 : 1 })} ${u[i]}`;
  };

  const formatUploadDate = (iso: string): string => {
    try {
      return new Date(iso).toLocaleString(dateLocale, {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch {
      return "—";
    }
  };

  const statusLabel = (status: string): string => {
    const s = status.toUpperCase();
    if (s === "READY") return t("statusReady");
    if (s === "PROCESSING") return t("statusProcessing");
    return status;
  };

  return (
    <Card className="rounded-2xl border-border/70 shadow-md">
      <CardHeader>
        <CardTitle>{t("title")}</CardTitle>
        <CardDescription>{t("subtitle")}</CardDescription>
      </CardHeader>
      <CardContent>
        {loading ? (
          <div className="space-y-3" aria-busy="true">
            {[1, 2, 3].map((i) => (
              <div
                key={i}
                className="h-12 animate-pulse rounded-xl bg-muted/60 dark:bg-muted/30"
              />
            ))}
            <p className="text-center text-xs text-muted-foreground">
              {t("loading")}
            </p>
          </div>
        ) : files.length === 0 && !uploading ? (
          <p className="rounded-xl border border-dashed border-border/80 py-10 text-center text-sm text-muted-foreground">
            {accountConfigured ? t("emptyIndexed") : t("emptyNeedAccount")}
          </p>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-border/80">
            <table className="w-full min-w-[520px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-border bg-muted/30 text-left text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  <th className="px-3 py-2.5 font-medium">{t("colName")}</th>
                  <th className="px-3 py-2.5 font-medium">{t("colUploaded")}</th>
                  <th className="px-3 py-2.5 font-medium">{t("colSize")}</th>
                  <th className="px-3 py-2.5 font-medium">{t("colStatus")}</th>
                  <th className="px-3 py-2.5 font-medium text-right">
                    {t("colActions")}
                  </th>
                </tr>
              </thead>
              <tbody>
                {uploading ? (
                  <tr className="border-b border-border/60 bg-amber-500/5">
                    <td className="max-w-[200px] truncate px-3 py-3 font-medium">
                      {uploading.fileName}
                    </td>
                    <td className="px-3 py-3 text-muted-foreground">—</td>
                    <td className="px-3 py-3 text-muted-foreground">
                      {formatFileSize(uploading.sizeBytes)}
                    </td>
                    <td className="px-3 py-3">
                      <span
                        className={cn(
                          "inline-flex rounded-full bg-amber-500/15 px-2 py-0.5 text-xs font-medium text-amber-700 dark:text-amber-400",
                        )}
                      >
                        {t("statusProcessing")}
                      </span>
                    </td>
                    <td className="px-3 py-3 text-right text-muted-foreground">
                      …
                    </td>
                  </tr>
                ) : null}
                {files.map((f) => (
                  <tr
                    key={f.batchId}
                    className="border-b border-border/60 last:border-0"
                  >
                    <td className="max-w-[220px] truncate px-3 py-3 font-medium">
                      {f.fileName || "—"}
                    </td>
                    <td className="whitespace-nowrap px-3 py-3 text-muted-foreground">
                      {formatUploadDate(f.uploadedAt)}
                    </td>
                    <td className="whitespace-nowrap px-3 py-3 text-muted-foreground">
                      {formatFileSize(f.sizeBytes)}
                    </td>
                    <td className="px-3 py-3">
                      <span className="inline-flex rounded-full bg-emerald-500/10 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:text-emerald-400">
                        {statusLabel(f.status)}
                      </span>
                    </td>
                    <td className="px-3 py-3 text-right">
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="rounded-lg text-destructive hover:bg-destructive/10 hover:text-destructive"
                        disabled={deleteBusyBatchId === f.batchId}
                        onClick={() =>
                          onDeleteRequest(f.batchId, f.fileName || f.batchId)
                        }
                      >
                        {deleteBusyBatchId === f.batchId
                          ? t("deleting")
                          : t("delete")}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
