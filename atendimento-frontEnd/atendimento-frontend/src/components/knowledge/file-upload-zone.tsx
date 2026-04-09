"use client";

import { FileText, Upload } from "lucide-react";
import * as React from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

const ACCEPT = ".pdf,.txt,application/pdf,text/plain";

type FileUploadZoneProps = {
  disabled?: boolean;
  pendingFile: File | null;
  onFileChange: (file: File | null) => void;
};

export function FileUploadZone({
  disabled,
  pendingFile,
  onFileChange,
}: FileUploadZoneProps) {
  const t = useTranslations("knowledgeUpload");
  const inputRef = React.useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = React.useState(false);

  const pick = () => inputRef.current?.click();

  const handleFiles = (list: FileList | null) => {
    if (!list?.length) return;
    const file = list[0];
    const lower = file.name.toLowerCase();
    if (!lower.endsWith(".pdf") && !lower.endsWith(".txt")) {
      toast.error(t("invalidType"));
      return;
    }
    onFileChange(file);
  };

  return (
    <div className="space-y-3">
      <div
        className={cn(
          "rounded-2xl border-2 border-dashed border-border/80 bg-muted/25 p-8 text-center shadow-inner transition-colors",
          dragOver && "border-primary/60 bg-primary/5",
          disabled && "pointer-events-none opacity-50",
        )}
        onDragOver={(e) => {
          e.preventDefault();
          setDragOver(true);
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragOver(false);
          handleFiles(e.dataTransfer.files);
        }}
      >
        <input
          ref={inputRef}
          type="file"
          accept={ACCEPT}
          className="hidden"
          disabled={disabled}
          onChange={(e) => handleFiles(e.target.files)}
        />
        <Upload className="mx-auto h-10 w-10 text-muted-foreground" />
        <p className="mt-3 text-sm font-medium">{t("dropTitle")}</p>
        <p className="mt-1 text-xs text-muted-foreground">{t("dropHint")}</p>
        <Button
          type="button"
          variant="secondary"
          className="mt-4 rounded-xl shadow-sm"
          disabled={disabled}
          onClick={pick}
        >
          {t("chooseFile")}
        </Button>
      </div>

      {pendingFile ? (
        <div className="flex items-center gap-3 rounded-xl border border-border/80 bg-card px-4 py-3 shadow-sm">
          <FileText className="h-5 w-5 shrink-0 text-primary" />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">{pendingFile.name}</p>
            <p className="text-xs text-muted-foreground">
              {t("readyKb", {
                size: (pendingFile.size / 1024).toFixed(1),
              })}
            </p>
          </div>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="shrink-0"
            disabled={disabled}
            onClick={() => onFileChange(null)}
          >
            {t("remove")}
          </Button>
        </div>
      ) : null}
    </div>
  );
}
