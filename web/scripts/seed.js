import { ensureSeeded } from '../src/core/seed.js';

ensureSeeded()
  .then(() => {
    console.log('seed ok');
    process.exit(0);
  })
  .catch((e) => {
    console.error(e);
    process.exit(1);
  });
