"use strict";

const GOOGLE_WEB_CLIENT_ID = "1025325431669-l4rkq07l3f1rq3r7u0hbjd238a0te79p.apps.googleusercontent.com";
const DELETE_ENDPOINT = "https://just-notes-entitlement-api-dev-1025325431669.asia-east1.run.app/v1/account/delete";

let credential = null;

const checkbox = document.querySelector("#understand");
const confirmation = document.querySelector("#confirmation");
const deleteButton = document.querySelector("#delete-account");
const status = document.querySelector("#status");

function updateButton() {
  deleteButton.disabled = !(credential && checkbox.checked && confirmation.value === "DELETE");
}

function setStatus(message) {
  status.textContent = message;
}

function initializeGoogleSignIn() {
  if (!window.google?.accounts?.id) {
    setStatus("Google sign-in could not load. Please refresh or use the support email below.");
    return;
  }
  window.google.accounts.id.initialize({
    client_id: GOOGLE_WEB_CLIENT_ID,
    auto_select: false,
    cancel_on_tap_outside: true,
    callback: (response) => {
      credential = typeof response?.credential === "string" ? response.credential : null;
      setStatus(credential ? "Google authentication is ready. Complete both confirmations." : "Authentication failed. Please try again.");
      updateButton();
    },
  });
  window.google.accounts.id.renderButton(document.querySelector("#google-signin"), {
    type: "standard",
    theme: "outline",
    size: "large",
    text: "signin_with",
  });
}

checkbox.addEventListener("change", updateButton);
confirmation.addEventListener("input", updateButton);

deleteButton.addEventListener("click", async () => {
  if (!credential || !checkbox.checked || confirmation.value !== "DELETE") return;
  deleteButton.disabled = true;
  setStatus("Submitting the deletion request…");
  try {
    const response = await fetch(DELETE_ENDPOINT, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${credential}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ confirmation: "DELETE" }),
      cache: "no-store",
      credentials: "omit",
      referrerPolicy: "no-referrer",
    });
    if (response.status === 204) {
      setStatus("Deletion completed. Eligible Just Notes backend account data was removed.");
      checkbox.checked = false;
      confirmation.value = "";
    } else if (response.status === 409) {
      setStatus("Deletion is blocked by a subscription that has not ended. Cancel it in Google Play, wait until expiry, and try again.");
    } else if (response.status === 401) {
      setStatus("Authentication expired. Sign in again and repeat the confirmation.");
    } else {
      setStatus("Deletion could not be completed. Please use the support email below.");
    }
  } catch (_) {
    setStatus("Deletion could not be completed. Check your connection or use the support email below.");
  } finally {
    credential = null;
    updateButton();
  }
});

window.addEventListener("load", initializeGoogleSignIn, { once: true });
