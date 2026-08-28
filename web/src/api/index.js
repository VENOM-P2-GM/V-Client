import { Router } from 'express';
import system from './system.js';
import profiles from './profiles.js';
import accounts from './accounts.js';
import jobs from './jobs.js';
import news from './news.js';

const r = Router();
r.use('/system', system);
r.use('/profiles', profiles);
r.use('/accounts', accounts);
r.use('/jobs', jobs);
r.use('/news', news);

export default r;
