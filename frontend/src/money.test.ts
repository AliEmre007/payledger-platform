import { describe, expect, it } from "vitest";
import { formatMinor, parseMajorToMinor } from "./money";

describe("customer money formatting", () => {
  it("parses customer-facing decimal amounts into minor units", () => {
    expect(parseMajorToMinor("125")).toBe(12_500);
    expect(parseMajorToMinor("125.5")).toBe(12_550);
    expect(parseMajorToMinor("125.50")).toBe(12_550);
  });

  it("rejects amounts with more than two decimal places", () => {
    expect(() => parseMajorToMinor("125.505")).toThrow(
      "Amount must use up to two decimal places."
    );
  });

  it("formats minor units as customer-facing amounts", () => {
    expect(formatMinor(12_550, "TRY")).toBe("125.50 TRY");
    expect(formatMinor(-2_500, "TRY")).toBe("-25.00 TRY");
  });
});
