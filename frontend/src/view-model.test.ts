import { describe, expect, it } from "vitest";
import { navigationFor } from "./view-model";
import type { UserSession } from "./types";

describe("navigationFor", () => {
  it("returns no navigation while signed out", () => {
    expect(navigationFor(session(false, []))).toEqual([]);
  });

  it("hides operations screens from customer-only sessions", () => {
    expect(navigationFor(session(true, ["CUSTOMER"]))).toEqual([
      { id: "customer", label: "Customer" }
    ]);
  });

  it("shows operations screens for operations users", () => {
    expect(navigationFor(session(true, ["CUSTOMER", "OPERATIONS"]))).toEqual([
      { id: "customer", label: "Customer" },
      { id: "operations", label: "Operations" }
    ]);
  });
});

function session(
  authenticated: boolean,
  roles: UserSession["roles"]
): UserSession {
  return {
    authenticated,
    subject: authenticated ? "subject" : "",
    username: authenticated ? "user" : "",
    roles
  };
}
