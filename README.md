# ChatPing

A chat mod for Minecraft that makes `@` mentions actually noticeable — with a ding, a proper
autocomplete dropdown, hover actions on every message, quoted replies, and (when installed on the
server) message editing and deletion.

Works **without the server mod** out of the box. Install it on the server as well and it unlocks
the extra features listed under [Server-side features](#server-side-features).

Repository: <https://github.com/Mewzica/Chat-Ping>

---

## Features

### `@name` mentions with a ding

Type `@` and a dropdown appears with the online players, exactly like vanilla command
autocomplete — arrow keys to move, `Tab` to complete, mouse works too.

```
@Steve when does the server restart?
```

Whoever is mentioned hears a note-block *ding* and sees the whole message in gold. Everyone else
sees just the `@Steve` part in blue.

Usernames are `[A-Za-z0-9_]` only, so a mention ends at the first character outside that set
(space, punctuation, or CJK character). A mention does **not** need a space in front of it —
`Hi@Steve` pings Steve just like `Hi @Steve` does. Only e-mail-shaped text (`a@Steve.com`) is
ignored.

### Hover actions on any message

Open chat and hover a message. A row of icons appears at the right edge:

| Icon | Action |
| --- | --- |
| `@` | Appends `@<sender> ` to the chat input (does not overwrite what you already typed) |
| `↩` | Starts a quoted reply — see below |
| `✎` | Edit the message (own messages only; requires the server mod) |
| `✕` | Delete the message (own messages, or anything if you are an admin; requires the server mod) |

One set of icons per message, drawn on the last line — long messages that wrap do not get an icon
row on every line.

System messages (joins, deaths, command feedback, server broadcasts) show no icons for regular
players. Admins get a single `✕` on them.

### Quoted replies

Clicking `↩` does not dump a wall of text into your input. Instead a small bar appears just above
the input:

```
↩ “<Steve> when does the server restart?”                         x
```

Type your reply and hit Enter. It is sent as:

```
@Steve “<Steve> when does the server restart?” 8pm
```

Every client with the mod renders the quoted part as its own grey italic line above the reply, and
because the message still contains `@Steve`, Steve gets the ding. The `x` on the right cancels the
pending reply.

Mentions inside the quoted text still count. If the original message was
`<Alex> Hi@Steve` and you reply to it, Steve gets pinged again — the thing you are replying to was
already calling him, so dropping that would lose information.

```
<Alex>  Hi@Steve
<you>   @Alex “<Alex> Hi@Steve” let me check   → Steve is pinged too
```

### `@admin`

```
@admin someone is spamming
```

Mentioning `@admin` pings every online admin at once. Rendered in red bold so it is obvious this is
not a normal mention. If a real player happens to be named `admin`, the group mention is disabled
and `@admin` behaves as a normal mention for that player.

Sending `@admin` is open to everyone. The permission node below controls only who **receives** the
notification, not who can send it.

---

## Server-side features

Install ChatPing on the server as well and these become available:

- **Server-sent ding** — the server sends the sound packet, so the client mod does not play its
  own ding (avoiding a double sound).
- **Message editing** — click `✎`, the original text goes into the input, the message turns cyan,
  and the bar above the input shows `✎ Editing message`. Press Enter to commit. Broadcast to everyone.
- **Message deletion** — click `✕`, a vanilla-styled confirmation panel appears next to the button,
  and confirming removes the message for everyone. Admins can delete other players' messages and
  system messages too.
- **Client mod required** — if the server has the mod, clients without it are disconnected on join.
  Without this the two sides would render chat differently and edit/delete would not line up.

The server keeps a **message ledger** in memory (last 30 minutes, max 500 entries). Deleting a
message only marks its entry as deleted — it does not erase the record of who deleted what.
Entries outside the 30-minute window are no longer available for edit or delete:

```
[purge] Steve (admin) removed message: [<Alex> buy cheap gold here]
```

---

## Permissions

ChatPing reads permissions through [`fabric-permissions-api`](https://github.com/lucko/fabric-permissions-api),
which means it works with **LuckPerms** and any other plugin implementing that API. If no permission
plugin is installed, both nodes fall back to vanilla op levels.

| Node | Controls | Fallback |
| --- | --- | --- |
| `chatping.admin.deletemsg` | Deleting other players' messages and system messages | op level ≥ 2 |
| `chatping.admin.mention` | **Receiving** `@admin` notifications | op level ≥ 2 |

```
/lp group helper permission set chatping.admin.deletemsg true
/lp group mod    permission set chatping.admin.mention true
/lp user Steve   permission set chatping.admin.mention false
```

The permission check is done with reflection, so the same jar works on servers with and without
LuckPerms — no hard dependency.

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for your Minecraft version.
2. Grab the jar whose name ends with your Minecraft version — `chatping-0.1.0-beta-1.21.4-fabric.jar`
   is for 1.21.4, `…-1.20.1-fabric.jar` is for 1.20.1, and so on. There is no "one jar fits all"
   build.
3. Drop it into `.minecraft/mods/`.
4. Install [Fabric API](https://modrinth.com/mod/fabric-api) for the same version — required, since
   the networking and chat events come from it.

For servers: put the same jar in the server's `mods/` folder. Once the server has the mod, every
connecting client must have it too, or it gets disconnected on join.

---

## Version support

One source tree targets every Minecraft release from **1.20.1** through **26.3** — 23 versions in
total. Two pieces make that possible:

- **[Stonecutter](https://stonecutter.kikugie.dev/)** splits the project into one subproject per
  version and preprocesses the shared source with `//? if <version>` comments, so the handful of
  places where the game API actually broke can be branched in place.
- **Mojang's official mappings** instead of Yarn. Yarn stopped at 1.21.11 and the `26.x` line has
  no Yarn mappings at all, and the two naming schemes disagree on essentially every class name
  (`MinecraftClient` / `Minecraft`, `DrawContext` / `GuiGraphics`, …), so one codebase can only
  cover the whole range under a single set of names.
- **26.x ships unobfuscated.** From 26.1 Mojang no longer publishes mappings and the client jar
  already carries real names, so those five versions skip mappings altogether and apply
  `net.fabricmc.fabric-loom` — the newer plugin that turns obfuscation off — instead of the
  classic `fabric-loom`. Both are declared in `build.gradle` and exactly one is applied per
  version.

### Loaders

**Fabric and Quilt share one jar.** Quilt Loader reads `fabric.mod.json` natively, so there is
nothing to rebuild — install [Quilted Fabric API](https://modrinth.com/mod/qsl) in place of Fabric
API and drop the same `chatping-<mod>-<mc>-fabric.jar` in. Shipping a second, byte-identical
`-quilt.jar` would only put two identical files next to each other in a release, so this project
does not.

**Forge (≤ 1.20.4) and NeoForge** need a real port: a loader entry point, and a second networking
implementation, because the payload API differs on every loader. That work is not done yet.

Per-version build status is on the [Actions](https://github.com/Mewzica/Chat-Ping/actions) tab.

---

## Building

The build needs **JDK 25** and the bundled wrapper (Gradle 9.8.2) — Loom 1.19.3 requires both.

Build a single version:

```bash
./gradlew :1.20.1:build --configure-on-demand
```

The remapped jar lands in `versions/1.20.1/build/libs/`. Build everything at once with
`./gradlew build --configure-on-demand`.

`--configure-on-demand` is not optional in practice: without it Gradle configures all 23
subprojects on every invocation, which drags in all 23 Minecraft versions and Fabric API builds
before it even starts, and a single version that fails to configure takes down the whole build.

Artifact naming is `chatping-<mod-version>-<mc-version>-<loader>.jar`, for example
`chatping-0.1.0-beta-1.21.4-fabric.jar`. The version inside `fabric.mod.json` is the bare mod
version (`0.1.0-beta`) without the MC version suffix.

Per-version dependency versions live in `versions/<mc-version>/gradle.properties`
(`fabric_api_version`); everything shared is in the root `gradle.properties`.

---

## How it works

Because the base feature set is client-side only, there is no structured channel to carry metadata
between players — everything has to be encoded in the message text itself:

| Concept | Wire format |
| --- | --- |
| Mention | `@Steve hello` |
| Reply | `@Steve “<Steve> original text” your reply` |

The client parses these back out at render time, splits the quote onto its own line, and colours the
mention. Hover buttons are drawn by redirecting the single `GuiGraphics.drawString` call inside
`ChatComponent.render`, which gives the exact y coordinate of each rendered line; the chat-box
coordinate maths (`lineOffset`, `lineHeight`, `chatScale`) mirrors vanilla's own.

Autocomplete reuses vanilla's `CommandSuggestions` window: the mod replaces the
`SharedSuggestionProvider.suggest` call in the non-command branch with its own candidate list and
then asks vanilla to show the window, so scrolling, `Tab` completion and mouse selection all behave
exactly like command autocomplete.

When the server mod is present, editing and deletion go through custom packets
(`chatping:edit`, `chatping:purge` → `chatping:update`, `chatping:remove`). Edits are located by
sender + body. Deletions are located by the full rendered line, because the client must also be
able to delete system messages, which are not in the ledger at all — the client reports what the
line looked like on screen.

---

## Notes & limitations

- **Quoted replies lose server-side colours** — the quote is rebuilt from the plain text of the
  hovered line.
- Quotes are truncated at 90 characters so a long message cannot flood the chat box. Mentions inside
  the retained part of the quote still count; mentions past the truncation point are lost.
- The message ledger is in-memory only; restarting the server clears it, and messages older than
  30 minutes can no longer be edited or deleted.
- `@admin` only reaches players who hold `chatping.admin.mention` (or are op, without LuckPerms).

## License

This project is licensed under the MIT License.