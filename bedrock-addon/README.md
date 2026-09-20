# Bedrock Analyzer UI

This is the working source project for the in-world UI of Bedrock Analyzer.

The UI uses the current @bedrock-core/ui JSX runtime and @bedrock-core/ui/ore-styled components. The upstream project documents that the matching render pack is required in the world; the library and render pack must come from the same release.

## Build

From this directory:

```bash
npm install
npm run build
```

The build uses the Bedrock Core bundler through Regolith and produces a Minecraft-ready behavior pack.

## What happens in game

On the player's first spawn, the pack renders the Bedrock Analyzer screen. It is a separate addon screen and does not replace or modify the official Minecraft main menu.

## Important

The analyzer only reports screen-visible, heuristic observations. It cannot inspect another player's device or prove that a player has cheats.
