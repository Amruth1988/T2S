import { SecretManagerServiceClient } from "@google-cloud/secret-manager";
import { initializeApp } from "firebase-admin/app";
import { HttpsError, onCall } from "firebase-functions/v2/https";

initializeApp();
const secrets = new SecretManagerServiceClient();
const REGION = "asia-south1";
const PROJECT_ID = process.env.GCLOUD_PROJECT!;

// Secret resources must be provisioned before deployment. The function runtime
// service account receives Secret Manager Secret Version Adder on these secrets only.
const PROVIDERS = {
  sarvam: "t2s-sarvam-api-key",
  vakyam: "t2s-vakyam-api-key",
  india: "t2s-india-tts-api-key",
} as const;
type Provider = keyof typeof PROVIDERS;

function requireAdmin(request: { auth?: { uid: string; token: Record<string, unknown> } | null }): string {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in is required.");
  if (request.auth.token.admin !== true) {
    throw new HttpsError("permission-denied", "Only T2S administrators can manage central provider keys.");
  }
  return request.auth.uid;
}

function providerName(value: unknown): Provider {
  if (typeof value !== "string" || !(value in PROVIDERS)) {
    throw new HttpsError("invalid-argument", "Unsupported provider.");
  }
  return value as Provider;
}

export const saveCentralProviderKey = onCall({ region: REGION, enforceAppCheck: true }, async (request) => {
  const uid = requireAdmin(request);
  const provider = providerName(request.data?.provider);
  const key = request.data?.apiKey;
  if (typeof key !== "string" || key.trim().length < 8 || key.length > 4096) {
    throw new HttpsError("invalid-argument", "Enter a valid provider key.");
  }
  const name = `projects/${PROJECT_ID}/secrets/${PROVIDERS[provider]}`;
  try {
    await secrets.addSecretVersion({
      parent: name,
      payload: { data: Buffer.from(key.trim(), "utf8") },
    });
  } catch (error) {
    // Never include the submitted key or provider response in logs/errors.
    console.error("Central provider key save failed", { provider, adminUid: uid });
    throw new HttpsError("failed-precondition", "Central secret is not provisioned or the function lacks Secret Manager permission.");
  }
  return { saved: true, provider, message: "Provider key saved securely." };
});

export const getCentralProviderStatus = onCall({ region: REGION, enforceAppCheck: true }, async (request) => {
  requireAdmin(request);
  const entries = await Promise.all(Object.entries(PROVIDERS).map(async ([provider, secretId]) => {
    try {
      const [versions] = await secrets.listSecretVersions({
        parent: `projects/${PROJECT_ID}/secrets/${secretId}`,
        pageSize: 1,
        filter: "state:ENABLED",
      });
      return [provider, Boolean(versions.length)] as const;
    } catch {
      return [provider, false] as const;
    }
  }));
  return Object.fromEntries(entries);
});
