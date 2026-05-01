"use client";

import { Eye, EyeOff } from "lucide-react";
import * as React from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

export type PasswordInputProps = Omit<React.ComponentProps<typeof Input>, "type"> & {
  toggleShowLabel: string;
  toggleHideLabel: string;
};

/**
 * Password field with visibility toggle for accessibility (recommended over placeholder-only cues).
 */
export const PasswordInput = React.forwardRef<HTMLInputElement, PasswordInputProps>(
  ({ className, toggleShowLabel, toggleHideLabel, id, disabled, ...rest }, ref) => {
    const [visible, setVisible] = React.useState(false);
    const inputId = id ?? React.useId();
    const btnId = `${inputId}-visibility`;

    return (
      <div className="relative">
        <Input
          ref={ref}
          id={inputId}
          type={visible ? "text" : "password"}
          disabled={disabled}
          className={cn("pr-12 min-h-11", className)}
          autoComplete={rest.autoComplete ?? "current-password"}
          {...rest}
        />
        <Button
          id={btnId}
          type="button"
          variant="ghost"
          size="icon"
          disabled={disabled}
          className="absolute right-1 top-1/2 h-9 w-9 -translate-y-1/2 text-muted-foreground hover:text-foreground"
          aria-pressed={visible}
          aria-label={visible ? toggleHideLabel : toggleShowLabel}
          aria-controls={inputId}
          onClick={() => setVisible((v) => !v)}
        >
          {visible ? <EyeOff className="h-4 w-4" aria-hidden /> : <Eye className="h-4 w-4" aria-hidden />}
        </Button>
      </div>
    );
  },
);
PasswordInput.displayName = "PasswordInput";
