# ChatPing

A chat mod for Minecraft that makes `@` mentions actually noticeable — with a ding, a proper
autocomplete dropdown, hover actions on every message, quoted replies, and (when installed on the
server) message editing and deletion.

Works **client-only** out of the box. Install it on the server too and it unlocks the extra
features listed under [Server-side features](#server-side-features).

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

Usernames are `[A-Za-z0-9_]` only, so a mention ends naturally at a space, punctuation, or CJK
character. A mention does **not** need a space in front of it — `Hi@Steve` pings Steve just like
`Hi @Steve` does. Only e-mail-shaped text (`a@Steve.com`) is ignored.

### Hover actions on any message

Open chat and hover a message. A row of icons appears at the right edge:

| Icon | Action |
| --- | --- |
| `@` | Appends `@<sender> ` to the chat input (does not overwrite what you already typed) |
| `↩` | Starts a quoted reply — see below |
| `✎` | Edit the message (own messages only, requires the server mod) |
| `✕` | Delete the message (own messages, or anything if you are an admin) |

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

Sending `@admin` is not restricted — anyone can call an admin. Who *receives* it is controlled by a
permission node (see below).

---

## Server-side features

Install ChatPing on the server as well and these become available:

- **Server-sent ding** — the server sends the sound packet, so even players on a vanilla client
  hear it. The client stops playing its own ding to avoid a double sound.
- **Message editing** — click `✎`, the original text goes into the input, the message turns cyan,
  and the bar above the input shows `✎ Editing message`. Press Enter to commit. Broadcast to everyone.
- **Message deletion** — click `✕`, a vanilla-styled confirmation panel appears next to the button,
  and confirming removes the message for everyone. Admins can delete other players' messages and
  system messages too.
- **Client mod required** — if the server has the mod, clients without it are disconnected on join.
  Without this the two sides would render chat differently and edit/delete would not line up.

The server keeps a **message ledger** in memory (last 30 minutes, max 500 entries). Deletions only
mark an entry as deleted — nothing is ever removed from the ledger, so the server retains a record
of what was deleted and by whom:

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

1. Install [Fabric Loader](https://fabricmc.net/use/) for the matching Minecraft version.
2. Drop `chatping-<mod-version>-<mc-version>-fabric.jar` into `.minecraft/mods/`.
3. Install [Fabric API](https://modrinth.com/mod/fabric-api) — required since the server module
   uses its networking and chat events.

For servers: put the same jar in the server's `mods/` folder. Every connecting client must have it.

---

## Version support

Currently built for **Minecraft 1.20.1 (Fabric)**.

The mod uses Mojang's official mappings rather than Yarn, so that one source tree can target every
release from 1.20.1 through the current `26.x` line (Yarn mappings stopped at 1.21.11). Multi-version
and multi-loader support — Fabric, Quilt, Forge (≤ 1.20.4) and NeoForge — is in progress.

---

## Building

```bash
./gradlew build
```

The remapped jar ends up in `build/libs/`.

Artifact naming is `chatping-<mod-version>-<mc-version>-<loader>.jar`, for example
`chatping-0.1.0-beta-1.20.1-fabric.jar`. The version inside `fabric.mod.json` is the bare mod
version (`0.1.0-beta`) without the MC version suffix.

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
(`chatping:edit`, `chatping:purge` → `chatping:update`, `chatping:remove`). Messages are located by
sender + body for edits, and by full rendered line for deletions — system messages are not in the
ledger at all, so the client has to report what the line looked like on screen.

---

## Localization

Nothing is hard-coded. Every string the mod shows — the delete confirmation, the edit banner, the
server-side error messages, the kick reason — is a translation key resolved by Minecraft itself, so
it follows the player's language setting.

| File | |
| --- | --- |
| `assets/chatping/lang/en_us.json` | English (source of truth) |
| `assets/chatping/lang/zh_cn.json` | Simplified Chinese |

To add a language, drop a `<locale>.json` next to those two with the same keys. The keys are listed
in `en_us.json`; anything missing falls back to English.

---

## Notes & limitations

- **Quoted replies lose server-side colours** — the quote is rebuilt from the plain text of the
  hovered line.
- Quotes are truncated at 90 characters so a long message cannot flood the chat box.
- The message ledger is in-memory only; restarting the server clears it, and messages older than
  30 minutes can no longer be edited or deleted.
- `@admin` only reaches players who hold `chatping.admin.mention` (or are op, without LuckPerms).

## License

MIT
