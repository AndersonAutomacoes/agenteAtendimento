"use client";

import * as React from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";

import { DeleteFileDialog } from "@/components/knowledge/delete-file-dialog";
import { FileUploadZone } from "@/components/knowledge/file-upload-zone";
import { KnowledgeBaseFilesTable } from "@/components/knowledge/uploaded-files-list";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import {
  deleteKnowledgeFile,
  getKnowledgeBase,
  postIngest,
  toUserFacingApiError,
} from "@/services/apiService";

const TENANT_STORAGE_KEY = "cerebro-tenant-id";

export default function KnowledgePage() {
  const t = useTranslations("knowledge");
  const tApi = useTranslations("api");
  const translateApi = React.useCallback(
    (key: string) => tApi(key),
    [tApi],
  );

  const [tenantId, setTenantId] = React.useState("");
  const [pendingFile, setPendingFile] = React.useState<File | null>(null);
  const [uploading, setUploading] = React.useState(false);
  const [listLoading, setListLoading] = React.useState(false);
  const [files, setFiles] = React.useState<
    Awaited<ReturnType<typeof getKnowledgeBase>>
  >([]);
  const [pendingDelete, setPendingDelete] = React.useState<{
    batchId: string;
    fileName: string;
  } | null>(null);
  const [deleteBusy, setDeleteBusy] = React.useState(false);
  const [deleteBusyBatchId, setDeleteBusyBatchId] = React.useState<
    string | null
  >(null);

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

  const fetchList = React.useCallback(async (tid: string) => {
    setListLoading(true);
    try {
      const data = await getKnowledgeBase(tid);
      setFiles(data);
    } catch (e) {
      toast.error(toUserFacingApiError(e, translateApi));
      setFiles([]);
    } finally {
      setListLoading(false);
    }
  }, [translateApi]);

  React.useEffect(() => {
    const tid = tenantId.trim();
    if (!tid) {
      setFiles([]);
      setListLoading(false);
      return;
    }
    void fetchList(tid);
  }, [tenantId, fetchList]);

  const submit = async () => {
    const tid = tenantId.trim();
    if (!tid) {
      toast.error(t("toastNeedAccount"));
      return;
    }
    if (!pendingFile) {
      toast.error(t("toastNeedFile"));
      return;
    }
    setUploading(true);
    const dismiss = toast.loading(t("toastUploading", { name: pendingFile.name }));
    try {
      await postIngest(tid, pendingFile);
      toast.success(t("toastSuccess"), { id: dismiss });
      setPendingFile(null);
      await fetchList(tid);
    } catch (e) {
      toast.error(toUserFacingApiError(e, translateApi), { id: dismiss });
    } finally {
      setUploading(false);
    }
  };

  const confirmDelete = async () => {
    if (!pendingDelete) return;
    const tid = tenantId.trim();
    if (!tid) return;
    setDeleteBusy(true);
    setDeleteBusyBatchId(pendingDelete.batchId);
    try {
      await deleteKnowledgeFile(tid, pendingDelete.batchId);
      toast.success(t("toastDeleted"));
      setPendingDelete(null);
      await fetchList(tid);
    } catch (e) {
      toast.error(toUserFacingApiError(e, translateApi));
    } finally {
      setDeleteBusy(false);
      setDeleteBusyBatchId(null);
    }
  };

  const uploadingState =
    uploading && pendingFile
      ? { fileName: pendingFile.name, sizeBytes: pendingFile.size }
      : null;

  return (
    <div className="mx-auto max-w-3xl space-y-8">
      <DeleteFileDialog
        open={pendingDelete != null}
        fileName={pendingDelete?.fileName ?? ""}
        busy={deleteBusy}
        onCancel={() => {
          if (!deleteBusy) setPendingDelete(null);
        }}
        onConfirm={confirmDelete}
      />

      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{t("title")}</h1>
        <p className="text-muted-foreground">{t("intro")}</p>
      </div>

      <div className="space-y-1">
        <Label className="text-muted-foreground">{t("accountId")}</Label>
        <p
          className={cn(
            "min-h-9 text-base font-semibold tracking-tight text-foreground sm:text-lg",
            !tenantId.trim() && "font-normal text-muted-foreground",
          )}
          aria-label={t("accountId")}
        >
          {tenantId.trim() || "—"}
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
          {uploading ? t("sending") : t("send")}
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="rounded-xl"
          disabled={listLoading || !tenantId.trim()}
          onClick={() => void fetchList(tenantId.trim())}
        >
          {listLoading ? t("refreshing") : t("refreshList")}
        </Button>
      </div>

      <KnowledgeBaseFilesTable
        files={files}
        accountConfigured={Boolean(tenantId.trim())}
        loading={
          Boolean(tenantId.trim()) && listLoading && files.length === 0
        }
        uploading={uploadingState}
        deleteBusyBatchId={deleteBusyBatchId}
        onDeleteRequest={(batchId, fileName) =>
          setPendingDelete({ batchId, fileName })
        }
      />
    </div>
  );
}
