import { hasOperationsRole } from "./auth";
import type { Role, UserSession } from "./types";

export type NavigationItem = {
  id: "customer" | "operations";
  label: string;
};

export function navigationFor(session: UserSession): NavigationItem[] {
  if (!session.authenticated) {
    return [];
  }

  const items: NavigationItem[] = [{ id: "customer", label: "Customer" }];

  if (hasOperationsRole(session)) {
    items.push({ id: "operations", label: "Operations" });
  }

  return items;
}

export function roleLabel(roles: Role[]): string {
  return roles.length === 0 ? "No realm roles" : roles.join(" / ");
}
