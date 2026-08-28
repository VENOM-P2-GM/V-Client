import { Router } from 'express';

const r = Router();

r.get('/', (req, res) => {
  res.json({
    items: [
      {
        tag: 'v1.0.0',
        titleAr: 'إصدار V Client 1.0',
        titleEn: 'V Client 1.0 release',
        date: '2026-08-28',
        highlightsAr: [
          'بيئة معزولة كاملة لكل بروفايل (ملفات، مكتبات، جلسات، حفظ، سجلات)',
          'محرك V الداخلي لاختبار العزل مباشرة بدون إنترنت',
          'خط تشغيل ماينكرافت جاڤا كامل: manifest ← libraries ← assets ← JVM',
          'حسابات Offline + Microsoft (device code flow)',
          'واجهة عربية/إنجليزية مع دعم RTL',
          'كونسول تشغيل حي عبر WebSocket'
        ],
        highlightsEn: [
          'Full isolated environment per profile (files, libraries, session, saves, logs)',
          'Built-in V-Engine to test isolation directly, no internet needed',
          'Full Minecraft Java launch pipeline: manifest ← libraries ← assets ← JVM',
          'Offline + Microsoft (device code) accounts',
          'Arabic/English UI with RTL support',
          'Live launch console over WebSocket'
        ]
      },
      {
        tag: 'env',
        titleAr: 'كيف تشتغل البيئة المعزولة',
        titleEn: 'How the isolated environment works',
        date: '—',
        highlightsAr: [
          'كل بروفايل = مجلد مستقل تحت ~/.vclient/profiles/<id>',
          'عملية JVM/Node منفصلة لكل جلسة، بدون أي حالة مشتركة',
          'الجلسة (التوكن) تُكتب داخل بيئة البروفايل نفسها',
          'Assets: كاش مشترك اختياري أو نسخ كامل حسب الإعدادات'
        ],
        highlightsEn: [
          'Each profile = its own folder under ~/.vclient/profiles/<id>',
          'A separate process per session, no shared writable state',
          'The auth session is written inside the profile environment itself',
          'Assets: optional shared cache or full copy, per settings'
        ]
      }
    ]
  });
});

export default r;
