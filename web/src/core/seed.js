import { listProfiles, createProfile } from './profiles.js';
import { listAccounts, addOfflineAccount } from './accounts.js';

/** First-run seed so the client has a ready-to-launch environment. */
export async function ensureSeeded() {
  try {
    const profs = await listProfiles();
    if (profs.length === 0) {
      await createProfile({
        name: 'Default',
        engine: 'vengine',
        version: '1.1.0',
        description: 'Auto-created demo environment'
      });
      console.log('[seed] created default profile');
    }
    const accs = await listAccounts();
    if (accs.length === 0) {
      await addOfflineAccount('Venom');
      console.log('[seed] created offline account "Venom"');
    }
  } catch (e) {
    console.error('[seed] failed:', e.message);
  }
}
