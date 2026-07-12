import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const requiredPages = [
  "site/index.html",
  "site/privacy-policy/index.html",
  "site/terms-of-service/index.html",
  "site/account-deletion/index.html",
];
const requiredFiles = [...requiredPages, "site/styles.css", ".github/workflows/pages.yml"];

const failures = [];
const read = (relativePath) => {
  const absolutePath = path.join(root, relativePath);
  if (!fs.existsSync(absolutePath)) {
    failures.push(`Missing ${relativePath}`);
    return "";
  }
  return fs.readFileSync(absolutePath, "utf8");
};
const requireText = (body, pattern, message) => {
  if (!pattern.test(body)) failures.push(message);
};

requiredFiles.forEach(read);

for (const page of requiredPages) {
  const html = read(page);
  if (!html) continue;
  const h1Count = (html.match(/<h1\b/gi) ?? []).length;
  if (h1Count !== 1) failures.push(`${page} must contain exactly one h1`);
  requireText(html, /<html\s+lang="[^"]+"/i, `${page} needs a language declaration`);
  requireText(html, /<meta\s+name="viewport"/i, `${page} needs viewport metadata`);
  requireText(html, /(?:Effective|Updated)\s+(?:July 12, 2026|2026-07-12)/i, `${page} needs an updated date`);
  requireText(html, /<nav\b/i, `${page} needs navigation`);
  requireText(html, /Just Notes/i, `${page} must name Just Notes`);
}

const privacy = read("site/privacy-policy/index.html");
[
  [/Google Sign-In/i, "Privacy: Google Sign-In"],
  [/appDataFolder/, "Privacy: Drive appDataFolder"],
  [/Google Play Billing|Google Play purchase/i, "Privacy: Play Billing"],
  [/encrypted (?:purchase )?token|token ciphertext/i, "Privacy: encrypted purchase token"],
  [/KMS/, "Privacy: KMS"],
  [/Firestore/, "Privacy: Firestore"],
  [/Real-time Developer Notifications|RTDN/, "Privacy: RTDN"],
  [/retain|retention/i, "Privacy: retention"],
  [/delet/i, "Privacy: deletion"],
  [/does not sell/i, "Privacy: no sale"],
  [/yeh\.shibang@gmail\.com/i, "Privacy: support contact"],
].forEach(([pattern, label]) => requireText(privacy, pattern, label));

const terms = read("site/terms-of-service/index.html");
[
  [/subscription/i, "Terms: subscriptions"],
  [/eligible|eligibility/i, "Terms: eligible trial"],
  [/cancel/i, "Terms: cancellation"],
  [/refund/i, "Terms: refunds"],
  [/Do not use|acceptable use/i, "Terms: acceptable use"],
  [/availability|uninterrupted/i, "Terms: availability"],
  [/yeh\.shibang@gmail\.com/i, "Terms: contact"],
].forEach(([pattern, label]) => requireText(terms, pattern, label));

const deletion = read("site/account-deletion/index.html");
[
  [/cancel.*subscription/is, "Deletion: cancellation prerequisite"],
  [/Google Drive.*local|local.*Google Drive/is, "Deletion: Drive/local data scope"],
  [/confirmation|type\s+DELETE/i, "Deletion: explicit confirmation"],
  [/accounts\.google\.com\/gsi\/client/, "Deletion: Google Identity Services"],
  [/yeh\.shibang@gmail\.com/i, "Deletion: support fallback"],
].forEach(([pattern, label]) => requireText(deletion, pattern, label));

const combined = requiredPages.map(read).join("\n") + read("site/account-deletion/delete-account.js");
[
  [/TODO|TBD|lorem ipsum|coming soon/i, "unfinished marker"],
  [/google-analytics|googletagmanager|segment\.com|mixpanel/i, "analytics script"],
  [/raw[-_ ]?purchase[-_ ]?token\s*[:=]\s*["'][^"']+/i, "raw token example"],
].forEach(([pattern, label]) => {
  if (pattern.test(combined)) failures.push(`Forbidden ${label}`);
});
const publicEmailAddresses = combined.match(/[A-Z0-9._%+-]+@gmail\.com/gi) ?? [];
if (publicEmailAddresses.some((address) => address.toLowerCase() !== "yeh.shibang@gmail.com")) {
  failures.push("Forbidden non-support Gmail address");
}

const workflow = read(".github/workflows/pages.yml");
requireText(workflow, /path:\s*site\s*$/m, "Pages workflow must upload site/");
if (/path:\s*[.'"]+\s*$/m.test(workflow)) failures.push("Pages workflow must not upload repository root");

if (failures.length) {
  console.error(`Compliance site verification failed (${failures.length}):`);
  failures.forEach((failure) => console.error(`- ${failure}`));
  process.exit(1);
}

console.log("Compliance site verification passed: four pages, required disclosures, zero forbidden literals.");
