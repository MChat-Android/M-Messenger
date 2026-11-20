
import { createRemoteJWKSet, jwtVerify } from "jose";
import { RtcTokenBuilder, RtcRole } from "agora-token";

const FIREBASE_KEYS = createRemoteJWKSet(
  new URL(
    "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"
  )
);

const TOKEN_LIFETIME = 3600;

const HEADERS = {
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
  "Access-Control-Allow-Headers": "Authorization, Content-Type"
};

function respond(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: HEADERS
  });
}

async function verifyUser(request, projectId) {
  const auth = request.headers.get("Authorization") || "";

  if (!auth.startsWith("Bearer ")) {
    throw new Error("AUTH_REQUIRED");
  }

  const idToken = auth.slice(7).trim();

  if (!idToken) {
    throw new Error("AUTH_REQUIRED");
  }

  const { payload } = await jwtVerify(idToken, FIREBASE_KEYS, {
    algorithms: ["RS256"],
    audience: projectId,
    issuer: `https://securetoken.google.com/${projectId}`
  });

  if (
    typeof payload.sub !== "string" ||
    payload.sub.length < 1 ||
    payload.sub.length > 128
  ) {
    throw new Error("INVALID_USER");
  }

  return payload.sub;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: HEADERS
      });
    }

    if (request.method === "GET" && url.pathname === "/health") {
      return respond({
        ok: true,
        service: "M Messenger Agora Token"
      });
    }

    if (request.method !== "POST" || url.pathname !== "/rtc-token") {
      return respond({ error: "Not found" }, 404);
    }

    if (
      !env.AGORA_APP_ID ||
      !env.AGORA_APP_CERTIFICATE ||
      !env.FIREBASE_PROJECT_ID
    ) {
      return respond({
        error: "Server configuration is incomplete"
      }, 500);
    }

    try {
      const userAccount = await verifyUser(
        request,
        env.FIREBASE_PROJECT_ID
      );

      const body = await request.json();

      const channelName =
        typeof body.channelName === "string"
          ? body.channelName.trim()
          : "";

      const validChannel =
        /^[a-zA-Z0-9 !#$%&()+\-:;<=.>?@[\]^_{|}~,]+$/;

      if (
        channelName.length < 1 ||
        new TextEncoder().encode(channelName).length > 64 ||
        !validChannel.test(channelName)
      ) {
        return respond({ error: "Invalid channelName" }, 400);
      }

      const token = RtcTokenBuilder.buildTokenWithUserAccount(
        env.AGORA_APP_ID,
        env.AGORA_APP_CERTIFICATE,
        channelName,
        userAccount,
        RtcRole.PUBLISHER,
        TOKEN_LIFETIME,
        TOKEN_LIFETIME
      );

      if (!token) {
        return respond({
          error: "Could not generate Agora token"
        }, 500);
      }

      return respond({
        appId: env.AGORA_APP_ID,
        channelName,
        userAccount,
        token,
        expiresAt: Math.floor(Date.now() / 1000) + TOKEN_LIFETIME
      });

    } catch (error) {
      if (error.message === "AUTH_REQUIRED") {
        return respond({ error: "Firebase sign-in required" }, 401);
      }

      if (
        error.message === "INVALID_USER" ||
        error.code === "ERR_JWT_EXPIRED" ||
        error.code === "ERR_JWS_SIGNATURE_VERIFICATION_FAILED" ||
        error.code === "ERR_JWKS_NO_MATCHING_KEY" ||
        error.code === "ERR_JWT_CLAIM_VALIDATION_FAILED"
      ) {
        return respond({
          error: "Invalid or expired Firebase ID token"
        }, 401);
      }

      if (error instanceof SyntaxError) {
        return respond({ error: "Invalid JSON body" }, 400);
      }

      console.error("Agora token error:", error);

      return respond({ error: "Token service error" }, 500);
    }
  }
};
