import Keycloak from "keycloak-js";
import { config } from "./config";
import type { Role, UserSession } from "./types";

type RealmAccess = {
  roles?: string[];
};

export type AuthClient = {
  init: () => Promise<UserSession>;
  login: () => Promise<void>;
  logout: () => Promise<void>;
  token: () => Promise<string | null>;
};

export class KeycloakAuthClient implements AuthClient {
  private readonly keycloak = new Keycloak({
    url: config.keycloakUrl,
    realm: config.keycloakRealm,
    clientId: config.keycloakClientId
  });

  async init(): Promise<UserSession> {
    const authenticated = await this.keycloak.init({
      onLoad: "check-sso",
      pkceMethod: "S256",
      checkLoginIframe: false,
      silentCheckSsoFallback: false
    });

    return toSession(authenticated, this.keycloak.tokenParsed);
  }

  async login(): Promise<void> {
    await this.keycloak.login();
  }

  async logout(): Promise<void> {
    await this.keycloak.logout({
      redirectUri: window.location.origin
    });
  }

  async token(): Promise<string | null> {
    if (!this.keycloak.authenticated) {
      return null;
    }

    await this.keycloak.updateToken(30);
    return this.keycloak.token ?? null;
  }
}

export function hasOperationsRole(session: UserSession): boolean {
  return session.roles.includes("OPERATIONS") || session.roles.includes("ADMIN");
}

function toSession(
  authenticated: boolean,
  tokenParsed: Keycloak.KeycloakTokenParsed | undefined
): UserSession {
  if (!authenticated || !tokenParsed) {
    return {
      authenticated: false,
      subject: "",
      username: "",
      roles: []
    };
  }

  const realmAccess = tokenParsed.realm_access as RealmAccess | undefined;
  const roles = (realmAccess?.roles ?? []).filter(isPayLedgerRole);

  return {
    authenticated: true,
    subject: tokenParsed.sub ?? "",
    username:
      typeof tokenParsed.preferred_username === "string"
        ? tokenParsed.preferred_username
        : tokenParsed.sub ?? "authenticated-user",
    email: typeof tokenParsed.email === "string" ? tokenParsed.email : undefined,
    roles
  };
}

function isPayLedgerRole(role: string): role is Role {
  return role === "CUSTOMER" || role === "OPERATIONS" || role === "ADMIN";
}
