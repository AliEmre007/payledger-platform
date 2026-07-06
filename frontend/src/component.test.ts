import { describe, expect, it } from "vitest";
import { navigationFor } from "./view-model";
import type { UserSession } from "./types";

describe("authenticated navigation component contract", () => {
  it("renders customer and operations tabs for an operations token", () => {
    document.body.innerHTML = `<nav aria-label="Workspace"></nav>`;
    const nav = document.querySelector("nav");
    const session: UserSession = {
      authenticated: true,
      subject: "ops-subject",
      username: "ops",
      roles: ["OPERATIONS"]
    };

    if (!nav) {
      throw new Error("Missing test navigation element.");
    }

    nav.innerHTML = navigationFor(session)
      .map((item) => `<button data-view="${item.id}">${item.label}</button>`)
      .join("");

    expect(nav.querySelector("[data-view='customer']")).not.toBeNull();
    expect(nav.querySelector("[data-view='operations']")).not.toBeNull();
  });

  it("does not render operations tab for a customer token", () => {
    document.body.innerHTML = `<nav aria-label="Workspace"></nav>`;
    const nav = document.querySelector("nav");
    const session: UserSession = {
      authenticated: true,
      subject: "customer-subject",
      username: "alice",
      roles: ["CUSTOMER"]
    };

    if (!nav) {
      throw new Error("Missing test navigation element.");
    }

    nav.innerHTML = navigationFor(session)
      .map((item) => `<button data-view="${item.id}">${item.label}</button>`)
      .join("");

    expect(nav.querySelector("[data-view='customer']")).not.toBeNull();
    expect(nav.querySelector("[data-view='operations']")).toBeNull();
  });
});
