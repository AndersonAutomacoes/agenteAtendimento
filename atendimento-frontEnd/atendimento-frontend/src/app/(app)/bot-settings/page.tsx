import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export default function BotSettingsPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">
          Configurações do bot
        </h1>
        <p className="text-muted-foreground">
          Personalização do tom, prompts e integrações — disponível em breve.
        </p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Em breve</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          Esta secção será expandida com formulários alinhados à API de
          configuração quando estiver disponível no backend.
        </CardContent>
      </Card>
    </div>
  );
}
