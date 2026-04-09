"use client";

import * as React from "react";
import { useTranslations } from "next-intl";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

type DeleteFileDialogProps = {
  open: boolean;
  fileName: string;
  busy: boolean;
  onCancel: () => void;
  onConfirm: () => void;
};

export function DeleteFileDialog({
  open,
  fileName,
  busy,
  onCancel,
  onConfirm,
}: DeleteFileDialogProps) {
  const t = useTranslations("knowledgeDelete");

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      role="presentation"
    >
      <button
        type="button"
        className="absolute inset-0 bg-black/70 backdrop-blur-[2px]"
        aria-label={t("close")}
        onClick={busy ? undefined : onCancel}
        disabled={busy}
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="delete-file-title"
        className={cn(
          "relative z-10 w-full max-w-md rounded-2xl border border-border bg-card p-6 shadow-2xl",
          "ring-1 ring-white/10 dark:bg-card dark:ring-white/10",
        )}
      >
        <h2
          id="delete-file-title"
          className="text-lg font-semibold tracking-tight"
        >
          {t("title")}
        </h2>
        <p className="mt-2 text-sm text-muted-foreground">
          {t("confirm", { fileName })}
        </p>
        <div className="mt-6 flex flex-wrap justify-end gap-2">
          <Button
            type="button"
            variant="outline"
            className="rounded-xl"
            onClick={onCancel}
            disabled={busy}
          >
            {t("cancel")}
          </Button>
          <Button
            type="button"
            variant="destructive"
            className="rounded-xl"
            onClick={() => void onConfirm()}
            disabled={busy}
          >
            {busy ? t("deleting") : t("delete")}
          </Button>
        </div>
      </div>
    </div>
  );
}
