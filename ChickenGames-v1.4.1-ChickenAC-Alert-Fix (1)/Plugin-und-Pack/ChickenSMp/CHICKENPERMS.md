# ChickenPerms

ChickenPerms is an in-project permission/group manager for the ChickenSMP Paper plugin.
It is inspired by common permission plugins; it is not LuckPerms and does not contain
LuckPerms code.

## Commands

All commands require `chickenperms.admin` (OP by default).

- `/chicken creategroup <name>`
- `/chicken deletegroup <name>` (the `default` group is protected)
- `/chicken list`
- `/chicken group <name> permission set <node>`
- `/chicken group <name> permission unset <node>`
- `/chicken group <name> prefix <text>` (supports `&` color codes)
- `/chicken group <name> suffix <text>` (supports `&` color codes)
- `/chicken group <name> weight <number>`
- `/chicken parent add <child> <parent>`
- `/chicken user <player> parent set <group>`
- `/chicken user <player> permission set <node> [30m|2h|7d]`
- `/chicken user <player> permission unset <node>`
- `/chicken info <player-or-group>`
- `/chicken sync`

Data is stored in `plugins/ChickenSMPCore/chickenperms.yml`. Group permissions inherit
through parent groups. User permission nodes override inherited nodes. Wildcards are
resolved against Bukkit permissions registered by loaded plugins. Temporary user
permission nodes expire automatically when permissions are recalculated.

## Current limitations

This build currently uses YAML only. MySQL synchronization, a browser-based editor,
temporary group memberships, context-specific permissions, tracks, verbose tracing,
and cross-server synchronization are not implemented. The `editor` subcommand reports
that the editor is not yet available. The plugin must be compiled against the Paper
API version declared in `pom.xml` (Paper 1.21.11, Java 21).
