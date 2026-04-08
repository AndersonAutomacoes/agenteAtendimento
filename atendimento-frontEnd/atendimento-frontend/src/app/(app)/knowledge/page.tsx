"use client";

import * as React from "react";
import { toast } from "sonner";

import { FileUploadZone } from "@/components/knowledge/file-upload-zone";
import { UploadedFilesList } from "@/components/knowledge/uploaded-files-list";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { postIngest } from "@/services/apiService";

const TENANT_STORAGE_KEY = "cerebro-tenant-id";

export default function KnowledgePage() {
  const [tenantId, setTenantId] = React.useState("");
  const [pendingFile, setPendingFile] = React.useState<File | null>(null);
  const [uploading, setUploading] = React.useState(false);

  React.useEffect(() => {
    try {
      const v = localStorage.getItem(TENANT_STORAGE_KEY);
      if (v) setTenantId(v);
    } catch {
      /* ignore */
    }
  }, []);

  const persistTenant = (value: string) => {
    setTenantId(value);
    try {
      localStorage.setItem(TENANT_STORAGE_KEY, value);
    } catch {
      /* ignore */
    }
  };

  const submit = async () => {
    const tid = tenantId.trim();
    if (!tid) {
      toast.error("Indique o tenantId antes de enviar.");
      return;
    }
    if (!pendingFile) {
      toast.error("Escolha um ficheiro PDF ou TXT.");
      return;
    }
    setUploading(true);
    const dismiss = toast.loading(`A enviar ${pendingFile.name}…`);
    try {
      const result = await postIngest(tid, pendingFile);
      toast.success(
        `Ingestão concluída: ${result.chunksIngested} fragmento(s) indexados.`,
        { id: dismiss },
      );
      setPendingFile(null);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Falha na ingestão";
      toast.error(msg, { id: dismiss });
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="mx-auto max-w-3xl space-y-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">
          Base de conhecimento
        </h1>
        <p className="text-muted-foreground">
          Envie PDF ou TXT para enriquecer o RAG. O tenantId simula a conta do
          cliente no backend.
        </p>
      </div>

      <div className="space-y-2">
        <Label htmlFor="tenantId">Tenant ID</Label>
        <Input
          id="tenantId"
          placeholder="ex.: tenant-demo"
          value={tenantId}
          onChange={(e) => persistTenant(e.target.value)}
          autoComplete="off"
          className="rounded-xl"
        />
        <p className="text-xs text-muted-foreground">
          Guardado localmente neste browser para ingestão e chat.
        </p>
      </div>

      <FileUploadZone
        disabled={uploading}
        pendingFile={pendingFile}
        onFileChange={setPendingFile}
      />

      <div className="flex flex-wrap items-center gap-3">
        <Button
          type="button"
          className="rounded-xl shadow-md"
          disabled={uploading || !pendingFile}
          onClick={submit}
        >
          {uploading ? "A enviar…" : "Enviar"}
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="rounded-xl"
          onClick={() =>
            toast.info("Lista real virá quando o backend expuser um GET.")
          }
        >
          Atualizar lista (mock)
        </Button>
      </div>

      <UploadedFilesList />
    </div>
  );
}
