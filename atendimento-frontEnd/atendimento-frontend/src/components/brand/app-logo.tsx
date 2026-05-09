import Image from "next/image";

import { cn } from "@/lib/utils";

/** Canvas único para light/dark (PNG padronizado 1024×558, transparência). */
const INTRINSIC_WIDTH = 1024;
const INTRINSIC_HEIGHT = 558;

export type AppLogoVariant = "navigation" | "auth" | "marketing";

type AppLogoProps = {
  variant?: AppLogoVariant;
  /** Aumentar visibilidade na landing (above-the-fold). */
  priority?: boolean;
  className?: string;
};

/** Logos tema claro/escuro conforme classe `dark` no `<html>`. */
export function AppLogo({
  variant = "navigation",
  priority = false,
  className,
}: AppLogoProps) {
  const sizeClass =
    variant === "auth"
      ? "h-16 sm:h-[4.25rem]"
      : variant === "marketing"
        ? "h-12 sm:h-14"
        : "h-12";

  return (
    <span className={cn("inline-flex max-w-full shrink-0", className)}>
      <Image
        src="/branding/logo-light-ui.png"
        width={INTRINSIC_WIDTH}
        height={INTRINSIC_HEIGHT}
        sizes="(max-width: 640px) 85vw, (max-width: 1024px) 40vw, 360px"
        priority={priority}
        className={cn(
          sizeClass,
          "aspect-[1024/558] w-auto max-w-full shrink-0 object-contain object-left dark:hidden",
        )}
        alt=""
        decoding="async"
      />
      <Image
        src="/branding/logo-dark-ui.png"
        width={INTRINSIC_WIDTH}
        height={INTRINSIC_HEIGHT}
        sizes="(max-width: 640px) 85vw, (max-width: 1024px) 40vw, 360px"
        priority={priority}
        className={cn(
          sizeClass,
          "hidden aspect-[1024/558] w-auto max-w-full shrink-0 object-contain object-left dark:block",
        )}
        alt=""
        decoding="async"
      />
    </span>
  );
}
