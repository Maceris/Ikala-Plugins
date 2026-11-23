# Ikala-Permissions

Permissions in the style of Bukkit's: dotted nodes like `lotomation.command.gamemode`, each with a
default (`true`, `false`, `op` or `not op`) and child nodes, plus named groups that inherit from
each other and per-player overrides. Plugins declare their nodes in YAML, in Bukkit's
`permissions:` format, and games decide who is an operator and which groups each player is in.
