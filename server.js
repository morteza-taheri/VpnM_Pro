const http = require('http');

const PORT = 3000;
const HOST = '0.0.0.0';

const server = http.createServer((req, res) => {
  res.writeHead(200, {
    'Content-Type': 'text/html; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Cache-Control': 'no-cache'
  });
  res.end(`<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>VpnM Pro - Android Project</title>
  <style>
    * { box-sizing: border-box; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      margin: 0;
      background: #0f172a;
      color: #f8fafc;
      text-align: center;
      padding: 24px;
    }
    .card {
      background: #1e293b;
      padding: 40px;
      border-radius: 16px;
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.4);
      border: 1px solid #334155;
      max-width: 560px;
      width: 100%;
    }
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 14px;
      background: rgba(56, 189, 248, 0.15);
      color: #38bdf8;
      border-radius: 9999px;
      font-size: 13px;
      font-weight: 600;
      margin-bottom: 16px;
      border: 1px solid rgba(56, 189, 248, 0.3);
    }
    .dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #22c55e;
      display: inline-block;
    }
    h1 {
      margin: 0 0 12px 0;
      font-size: 26px;
      font-weight: 700;
      color: #ffffff;
    }
    p {
      color: #94a3b8;
      font-size: 15px;
      line-height: 1.6;
      margin: 0 0 20px 0;
    }
    .status-box {
      display: flex;
      align-items: center;
      justify-content: space-between;
      background: #0f172a;
      padding: 12px 16px;
      border-radius: 8px;
      border: 1px solid #334155;
      margin-bottom: 20px;
      font-size: 13px;
      color: #94a3b8;
    }
    .status-box .val {
      color: #38bdf8;
      font-weight: 600;
    }
    .instructions {
      text-align: left;
      background: #0f172a;
      padding: 18px 22px;
      border-radius: 12px;
      font-size: 14px;
      color: #cbd5e1;
      border: 1px solid #334155;
    }
    .instructions strong {
      color: #f1f5f9;
    }
    .instructions ol {
      margin: 10px 0 0 0;
      padding-left: 20px;
    }
    .instructions li {
      margin-bottom: 8px;
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="badge"><span class="dot"></span> Native Android Project Ready</div>
    <h1>VpnM Pro</h1>
    <p>SoftEther VPN Client with VPNGate mirror sources, custom filters, split tunneling, and room caching.</p>
    
    <div class="status-box">
      <span>Native C++ (SoftEther Core)</span>
      <span class="val">Compiled (arm64, v7a, x86)</span>
    </div>

    <div class="instructions">
      <strong>How to build in Android Studio:</strong>
      <ol>
        <li>Click <strong>Settings</strong> (gear icon at top right)</li>
        <li>Choose <strong>Export to GitHub</strong> or <strong>Download ZIP</strong></li>
        <li>Open the project directory in <strong>Android Studio</strong></li>
        <li>Run the app on your Android device or emulator</li>
      </ol>
    </div>
  </div>
</body>
</html>`);
});

server.listen(PORT, HOST, () => {
  console.log(`Dev server successfully started and listening on http://${HOST}:${PORT}`);
});

process.on('SIGTERM', () => {
  server.close(() => process.exit(0));
});

process.on('SIGINT', () => {
  server.close(() => process.exit(0));
});
