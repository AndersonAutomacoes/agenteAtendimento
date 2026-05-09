import Image from "next/image";

import { cn } from "@/lib/utils";

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
      ? "h-11 max-h-11"
      : variant === "marketing"
        ? "h-10 max-h-10"
        : "h-9 max-h-9";

  return (
    <span className={cn("inline-flex max-w-full shrink-0", className)}>
      <Image
        src="/branding/logo-light-ui.jpg"
        width={INTRINSIC_WIDTH}
        height={INTRINSIC_HEIGHT}
        sizes="(max-width: 768px) 60vw, 240px"
        priority={priority}
        className={cn(
          sizeClass,
          "w-auto max-w-full object-contain object-left dark:hidden",
        )}
        alt=""
        decoding="async"
      />
      <Image
        src="/branding/logo-dark-ui.jpg"
        width={INTRINSIC_WIDTH}
        height={INTRINSIC_HEIGHT}
        sizes="(max-width: 768px) 60vw, 240px"
        priority={priority}
        className={cn(
          sizeClass,
          "hidden w-auto max-w-full object-contain object-left dark:block",
        )}
        alt=""
        decoding="async"
      />
    </span>
  );
}
