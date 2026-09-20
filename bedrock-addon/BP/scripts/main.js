import { world } from '@minecraft/server';

// The compiled @bedrock-core/ui screen is expected to call this entry point.
// Keep gameplay logic separate from UI so the analyzer can evolve independently.
world.afterEvents.playerSpawn.subscribe((event) => {
  // Reserved for analyzer state initialization.
});
