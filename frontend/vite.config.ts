import { defineConfig } from "vite";

export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      "/api": "http://localhost:18080",
      "/actuator": "http://localhost:18080",
      "/api-docs": "http://localhost:18080"
    }
  },
  test: {
    environment: "jsdom"
  }
});
