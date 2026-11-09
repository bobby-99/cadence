# Sync format

Cadence on the phone and Cadence for Windows stay in step through **one file**, `cadence-sync.json`, in a folder both can reach (Syncthing, OneDrive, Google Drive, a USB stick, anything that mirrors a folder). There is no server and no account. Neither app ever talks to the network; the folder tool does.

## How it works

Each device keeps its own copy of the list. Whenever something changes, and when the app opens, a device:

1. reads the shared file,
2. merges it with its own list (rules below),
3. applies whatever is newer to itself,
4. writes the file back only if the merge added something to it.

Because the merge is symmetrical, any number of devices converge to the same list, in any order.

## The file

```json
{
  "format": "cadence-sync",
  "version": 1,
  "device": "Pixel 8",
  "writtenAt": 1789000000000,
  "tasks": [
    {
      "uuid": "9f1c…32 hex digits…",
      "title": "Call mom",
      "notes": "",
      "dueDate": 20400,
      "dueTime": 1080,
      "priority": 3,
      "completed": false,
      "completedAt": null,
      "createdAt": 1788900000000,
      "reminders": "0,60",
      "recurrence": "{\"u\":\"WEEK\",\"i\":1,\"wd\":[6]}",
      "occurrence": 1,
      "series": "9f1c…",
      "durationMin": 30,
      "updatedAt": 1789000000000
    }
  ],
  "deleted": [ { "uuid": "…", "at": 1789000000000 } ]
}
```

| Field | Meaning |
|---|---|
| `uuid` | The task's identity on every device. 32 lowercase hex characters. |
| `dueDate` | Days since 1970-01-01, or absent for no date. |
| `dueTime` | Minutes after midnight, or absent for all-day. |
| `priority` | 0 none, 1 low, 2 medium, 3 high. |
| `reminders` | Comma-separated "minutes before due"; empty for none. |
| `recurrence` | The repeat rule as a JSON string (the same one the phone stores), or absent. |
| `occurrence`, `series` | Which repeat this is, and the `uuid` of the first task of its series. |
| `updatedAt` | Milliseconds since 1970-01-01 UTC of the last change to any field above. |
| `deleted` | Tasks removed on some device and when, so the removal reaches the others. |

Left out on purpose because they belong to one device: snooze, the phone-calendar event id, focus sessions and settings.

## Merge rules

For each `uuid`, across both lists and both `deleted` lists:

- Of the two copies of a task, the one with the larger `updatedAt` wins. On a tie the local copy stays.
- A deletion wins if its time is **at least** the winning task's `updatedAt`; otherwise the task is kept and the deletion dropped.
- Deletions older than 90 days are forgotten. A device that has been offline longer than that can bring old deleted tasks back.

The whole task is replaced, not individual fields: if two devices edit the same task between syncs, the later edit wins entirely. This keeps the rules simple and is rarely noticeable for a personal list.

Clocks matter: both devices should have the right time (automatic time on both is enough).

## Versions

`version` is 1. A reader that sees a higher number refuses the file instead of guessing. New optional fields can be added without raising the number; unknown fields are ignored.

## Conflict copies

Folder tools sometimes create copies like `cadence-sync (conflict).json` when two devices write at the same moment. Cadence only reads `cadence-sync.json`. The next sync repairs anything that was missed, because each device merges its own full list back in.
