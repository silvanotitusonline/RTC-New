const fs = require('fs');

const sourcePath = '/home/ubuntu/rtc-community-source/supabase/functions/dispatch-community-alerts/index.ts';
const outputPath = '/home/ubuntu/rtc-community-source/tmp_deploy_dispatch_community_alerts.json';
const payload = {
  project_id: 'pbzzfzfgwzwdstvnwzqu',
  name: 'dispatch-community-alerts',
  verify_jwt: false,
  entrypoint_path: 'index.ts',
  files: [
    {
      name: 'index.ts',
      content: fs.readFileSync(sourcePath, 'utf8'),
    },
  ],
};
fs.writeFileSync(outputPath, JSON.stringify(payload, null, 2) + '\n');
