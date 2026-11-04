# PlusCraft

Open-source, community-driven multiplayer & social client mod for Minecraft Java **1.21.11** (Fabric).
Developed by **LOVE-YT** (@Loveyt224) & **NotHeartYT**.

## Phase 1 (this build)

Press **H** in a world to open the PlusCraft Hub.

- **Host tab** – open your single-player world to friends (`/publish`), pick port, look up your public IP via STUN, copy a join address.
- **Friends tab** – local friends list (saved in `config/pluscraft.json`).
- **About tab** – credits and roadmap.

## Roadmap (not built yet)

- Automatic NAT traversal / relay (WebRTC, TURN) – needs a relay/signalling component
- Cross-world chat, DMs, group chats, presence – needs a messaging backend
- Account manager (Microsoft + offline profiles)
- Open cosmetics: animated capes, Blockbench models, emotes
- Media editor, Discord Rich Presence

## Build

GitHub Actions builds the jar on every push: open **Actions → build → Artifacts → PlusCraft**.
