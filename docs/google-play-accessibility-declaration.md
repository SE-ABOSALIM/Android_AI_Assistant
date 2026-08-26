# Google Play AccessibilityService declaration draft

This document records the Production Hardening 3/11 source audit and the draft
answers for the Google Play AccessibilityService declaration. It is not a
privacy policy and does not replace the prominent disclosure shown in the app.

## 1. Classification

Android AI Assistant is a general-purpose voice and automation assistant. It is
not being declared as an Accessibility Tool and it is not represented as a tool
whose primary purpose is to support people with disabilities.

`isAccessibilityTool=true` is not used. The service metadata leaves the value
absent, which retains the non-accessibility-tool/default-false classification.

## 2. Core purpose requiring AccessibilityService

**Play Console category:** App functionality.

**Draft answer:** Android AI Assistant uses AccessibilityService to carry out
explicit commands requested by the user when the requested operation requires
interaction with an app or Android user interface and no narrower application
API can perform that interaction. Representative actions include locating and
tapping a visible control, entering or clearing text, scrolling, performing a
gesture, navigating with Back/Home/Recents, opening supported system panels,
and completing supported device-setting interactions.

## 3. Automation behavior

Accessibility actions are initiated by an explicit user voice or typed command,
or by a deterministic sequence of steps in a custom command explicitly created
by the user. Examples include a user asking the assistant to tap a named button,
write text into the focused field, scroll the current screen, go back, take a
screenshot, or change a supported Quick Settings tile. Custom-command delays and
retries only continue the configured workflow; they do not choose or initiate a
new goal autonomously.

The current `onAccessibilityEvent` implementation does not initiate automation.
The service's Back-key handling only cancels or exits a user-started grid or
selection flow. The source audit found no independent event-driven path that
starts Accessibility automation without a user command or a user-configured
deterministic workflow.

## 4. Accessibility-derived data

When required by a requested action, the service can inspect the active window
and its `AccessibilityNodeInfo` hierarchy. Confirmed fields include:

- visible node text, content descriptions, field hints, and view resource IDs;
- node class and package information and current app/window information;
- node bounds, parent/child relationships, and control hierarchy;
- clickable, editable, focused, scrollable, checked, enabled, and visible state;
- Quick Settings labels and state used to locate and verify a requested tile.

The service uses that information locally to locate a control, choose the
requested action target, set or clear text, focus a field, scroll, perform a
gesture, navigate, interact with supported system panels, and complete other
supported user-requested UI operations.

Visible screen text, content descriptions, view IDs, selected element details,
and the node hierarchy are not included in backend requests and are not stored
in the app database or shared with a third party. A command request can include
one Accessibility-derived Boolean named `has_search_input`, which reports only
whether a search field is available; it does not include the field content,
screen text, or element identity. The backend uses that Boolean transiently for
command resolution and does not write it to command history or application
logs. Content-bearing matching diagnostics are disabled in release builds.
Debug builds can write candidate/interface labels and bounds to local Android
Logcat. Recognized microphone speech and
the installed-app catalog are separate, non-Accessibility data paths and are not
the basis of this declaration answer.

## 5. Collection and sharing answer

**Question:** Do you collect and/or share personal or sensitive data using the
accessibility capabilities?

**Draft answer: NO.** Source inspection found no Accessibility-derived visible
content, personal content, node details, or selected element identity sent off
device or shared. The only off-device value derived from inspecting the current
interface is the non-content yes/no `has_search_input` capability signal. It
does not identify a person, reveal field contents, or transmit the text or
identity of an interface element. Interface matching diagnostics are disabled
in release builds and remain local to Android Logcat in debug builds. This
answer must be reviewed if either data path
changes.

## 6. Final prominent disclosure copy

**Title:** Accessibility Service disclosure

> Android AI Assistant uses the Android Accessibility Service to carry out
> actions you explicitly request in other apps or on the device, including
> specific steps in custom commands you create.
>
> When needed for a requested action, the service may inspect the current screen
> interface hierarchy, including visible text, content descriptions, view
> identifiers, field hints, control state and position, and app or window
> information. It can find and activate controls, enter or clear text, focus
> fields, scroll, perform gestures and navigation or system actions, and
> interact with supported system panels.
>
> This information is processed only to locate and operate the interface needed
> for the action you requested. Visible screen text and element details are
> processed on this device; they are not sent to our backend, stored in the app
> database, or shared with third parties. The app sends only a yes-or-no signal
> indicating whether a search field is available with a command request.
> The backend uses this signal only while resolving the command and does not
> persist it.
> Content-bearing matching diagnostics are disabled in release builds. Debug
> builds may contain interface labels in the local Android system log.
>
> Accessibility access is separate from microphone and installed-app data. You
> can choose Not now and enable it later.

**Affirmative action:** Agree and continue

**Negative action:** Not now

Equivalent Turkish and Arabic translations are shipped in their localized
Android string resources. The disclosure consent version is 2 because the
release/debug logging statement changed in Production Hardening 5/11; prior
version-1 consent does not suppress the updated disclosure.

## 7. Play Store listing note

Android AI Assistant uses Android AccessibilityService, after an in-app
disclosure and the user's affirmative consent, to perform explicit
user-requested interface actions such as locating and tapping controls, entering
text, scrolling, gestures, and supported navigation or system interactions.

## 8. Declaration video checklist

Record one continuous, clearly readable sequence:

1. Start from cleared app data so disclosure consent is absent.
2. Open the application and reach Accessibility setup through the normal
   Permissions flow.
3. Show that the dedicated in-app disclosure appears before Android
   Accessibility Settings.
4. Slowly scroll the disclosure and keep every paragraph readable on video.
5. Tap **Not now** and show that Android Accessibility Settings does not open.
6. Trigger Accessibility setup again and show that the disclosure appears again.
7. Tap **Agree and continue**.
8. Show Android Accessibility Settings opening only after that affirmative action.
9. Enable Android AI Assistant's service and return to the app.
10. Issue a representative explicit command, such as asking the assistant to
    tap a named visible control or scroll the current screen, and show the
    resulting action.
11. Return to the normal setup flow and show that current consent does not cause
    the same disclosure to repeat.
12. If practical, capture a separate reset-state demonstration in which the
    service is manually enabled before in-app consent and show that an attempted
    Accessibility action is gated and routes back to the disclosure.

The video must show the real production UI and the entire disclosure text. Avoid
cuts that obscure the ordering of disclosure, consent, system settings, and the
representative feature.

## 9. Future-update rule

Any material change to AccessibilityService capabilities, accessed interface
information, transmission/storage/logging, or initiation behavior requires a
fresh source audit and review of the in-app disclosure, Play Console declaration,
listing note, and declaration video. If the user-facing disclosure must change
materially, increment `AccessibilityDisclosureConsent.CURRENT_VERSION` so an old
acceptance no longer satisfies the new disclosure.
