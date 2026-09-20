import { world } from '@minecraft/server';
import { render } from '@bedrock-core/ui';
import AnalyzerScreen from './UI/AnalyzerScreen';

world.afterEvents.playerSpawn.subscribe(({ player, initialSpawn }) => {
  if (!initialSpawn) return;
  render(AnalyzerScreen, player);
});
