export type FrontendConfig = {
  apiBaseUrl: string;
  keycloakUrl: string;
  keycloakRealm: string;
  keycloakClientId: string;
};

export const config: FrontendConfig = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? "",
  keycloakUrl: import.meta.env.VITE_KEYCLOAK_URL ?? "http://localhost:18081",
  keycloakRealm: import.meta.env.VITE_KEYCLOAK_REALM ?? "payledger-local",
  keycloakClientId:
    import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? "payledger-frontend"
};
