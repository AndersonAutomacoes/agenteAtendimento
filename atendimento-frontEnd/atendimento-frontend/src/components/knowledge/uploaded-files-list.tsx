import { FileText } from "lucide-react";

import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

export type MockFileItem = {
  id: string;
  name: string;
  sizeLabel: string;
  uploadedAt: string;
};

const MOCK_FILES: MockFileItem[] = [
  {
    id: "1",
    name: "politica-atendimento.pdf",
    sizeLabel: "1,2 MB",
    uploadedAt: "2026-04-01",
  },
  {
    id: "2",
    name: "faq-produto.txt",
    sizeLabel: "48 KB",
    uploadedAt: "2026-04-03",
  },
  {
    id: "3",
    name: "manual-resumido.pdf",
    sizeLabel: "890 KB",
    uploadedAt: "2026-04-05",
  },
];

export function UploadedFilesList() {
  return (
    <Card className="rounded-2xl border-border/70 shadow-md">
      <CardHeader>
        <CardTitle>Ficheiros na base</CardTitle>
        <CardDescription>
          Lista de exemplo até existir endpoint de listagem no backend.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <ul className="divide-y divide-border overflow-hidden rounded-xl border border-border/80">
          {MOCK_FILES.map((f) => (
            <li
              key={f.id}
              className="flex items-center gap-3 px-4 py-3 text-sm"
            >
              <FileText className="h-4 w-4 shrink-0 text-muted-foreground" />
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium">{f.name}</p>
                <p className="text-xs text-muted-foreground">
                  {f.sizeLabel} · {f.uploadedAt}
                </p>
              </div>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}
