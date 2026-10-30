# SECURITY.md — CleanDroid security & privacy documentation

Last reviewed: 2026-10-30 (review of the exact code in this repository, see "Static audit" below).

## 1. Threat model

CleanDroid is a device-maintenance tool with elevated storage privileges. The assets it protects are:
the user's files (integrity), the user's privacy (no telemetry, no exfiltration), and the user's
trust (destructive operations must be explicit and predictable).

The main risks to defend against are therefore:
1. **Data exfiltration** — a cleaner/AV app sees a lot of metadata; it must never be able to phone home.
2. **Over-privileged access** — permissions must map 1:1 to visible features, nothing more.
3. **Destructive-operation abuse** — shredding/cleaning must never touch undeclared paths.
4. **Component exposure** — other apps must not be able to drive this app's components.

## 2. Hard guarantees

- **No `INTERNET` permission.** The merged manifest contains no network permission and the code
  contains no network API usage (verified by audit below). On Android, a permission the app does not
  hold *cannot* be circumvented — this is enforced by the kernel-level network permission check.
- **No telemetry, no analytics, no logging.** There is not a single `Log.*` call in the codebase.
- **No backup.** `android:allowBackup="false"`; nothing from the app is included in cloud backups
  or device-transfer (`d2d`) flows.
- **Single exported component.** Only `MainActivity` is exported (launcher entry, required).
  `OpsService` is `exported="false"`.
- **Immutable PendingIntents.** Both notification content intents use `FLAG_IMMUTABLE`
  (prevents PendingIntent hijacking / intent-redirection attacks).
- **No dynamic code.** No `WebView`, no JavaScript engine, no `DexClassLoader`, no reflection-based
  loading, no downloaded code execution.
- **No secrets.** No API keys, tokens or credentials anywhere in the source or assets.
- **No SQL.** No database at all → no SQL-injection surface.

## 3. Destructive-operation safety

- The cleaner only deletes from explicitly enumerated junk categories and **always skips the
  `Android/` tree and `LOST.DIR`**. Files are only deleted after being shown to the user in the
  results list, and the "old downloads" group is **unchecked by default**.
- Empty directories are deleted **children-first** (bottom-up ordering), and `File.delete()` on a
  non-empty directory fails safely by design.
- The shredder never follows symlinks into unknown territory (operates only on `File` objects the
  user explicitly picked from the in-app browser or threat list).
- All long operations run in one cancellable slot: starting a new op cancels the previous one,
  and cancellation always runs cleanup (shred filler files are deleted even on cancel).

## 4. Permission matrix (and why each is needed)

See `README.md` table. Notable deliberate exclusions: `INTERNET`, `ACCESS_NETWORK_STATE`,
`CAMERA`, `LOCATION`, `CONTACTS`, `SMS`, `RECORD_AUDIO`, `READ_CALL_LOG`, `ACCESSIBILITY`,
`REQUEST_INSTALL_PACKAGES`. None are requested; none appear in the manifest.

## 5. Static audit (this repository)

Automated checks run against `app/src/main` (ripgrep-based, reproducible):

| # | Check | Result |
|---|---|---|
| 1 | Network permission/API usage (`INTERNET`, sockets, HTTP clients) | **clean** — zero hits outside comments |
| 2 | Exported components | **clean** — only the launcher activity |
| 3 | WebView / JS bridge / dynamic code loading / reflection | **clean** — none |
| 4 | PendingIntent mutability | **clean** — 2/2 use `FLAG_IMMUTABLE` |
| 5 | Hardcoded secrets (api_key, secret, password, token) | **clean** — none |
| 6 | SQL / database usage | **clean** — none |
| 7 | Logging (`Log.*`, `println`, `printStackTrace`) | **clean** — none |
| 8 | `allowBackup` / `debuggable` | **clean** — `allowBackup=false`, `debuggable` unset (false in release) |
| 9 | R8 resource/minify shrinking for release builds | enabled |

Additional design-level mitigations:
- All JSON parsing uses the platform `org.json` (no reflection-based deserialization).
- Random bytes for shredding come from `SecureRandom` (CSPRNG), not `Random`.
- The app targets SDK 36 with `minifyEnabled` + `shrinkResources` release builds.

**Known vulnerabilities found: none.** This is a static review of the generated code, not a
dynamic penetration test; if you distribute the APK, also review your signing-key hygiene.

## 6. MASVS (OWASP Mobile Application Security Verification Standard) mapping

| MASVS area | Status |
|---|---|
| MASVS-STORAGE | Data stored: none outside system caches. Shredder documented with honest limits. |
| MASVS-CRYPTO | `SecureRandom` used; SHA-256 used for signature matching (integrity, not secrecy). |
| MASVS-AUTH | N/A — no accounts, no network. |
| MASVS-NETWORK | App cannot access the network (no permission, no code). |
| MASVS-PLATFORM | Minimum permission set; special permissions via official Settings flows; FGS with declared type; immutable PendingIntents; no exported components beyond launcher. |
| MASVS-CODE | No dynamic code, no WebView, R8 minified release builds. |
| MASVS-RESILIENCE | No secrets to extract; offline by design; nothing to reverse that yields user data. |

## 7. Antivirus signature DB — import format

The bundled DB is deliberately minimal and verifiable (EICAR test signature). To add real hashes,
append entries to `app/src/main/assets/av_signatures.json` — **only from sources you trust**:

```json
{
  "sha256": "<64 hex chars, SHA-256 of the whole file/APK>",
  "name": "Android/Trojan.Family.Name",
  "severity": 2,
  "note": "why this hash is malicious"
}
```

Recommended reputable sources: abuse.ch MalwareBazaar (free API key, tag `Android`),
VirusShare (account), university Android-malware datasets (e.g. CICMalDroid, Drebin, MH-100K).
Each hash is matched against the **full file content** of installed APKs and files under
`Downloads/`, so a hash must be of the complete original file, not a segment.

## 8. Reporting

If you find a vulnerability, do NOT open a public issue with exploit details. Contact the
maintainer privately and allow reasonable time for a fix before disclosure.
