const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");

const apkDir = path.resolve(__dirname, "../app/build/outputs/apk/debug");
const apkName = "suishouji-mate70pro-debug.apk";
const apkPath = path.join(apkDir, apkName);
const port = Number(process.env.SUISHOUJI_APK_PORT || 8765);

if (!fs.existsSync(apkPath)) {
  console.error(`APK not found: ${apkPath}`);
  process.exit(1);
}

const server = http.createServer((req, res) => {
  const requested = decodeURIComponent((req.url || "/").split("?")[0]);
  if (requested !== "/" && requested !== `/${apkName}`) {
    res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
    res.end("not found");
    return;
  }

  res.writeHead(200, {
    "Content-Type": "application/vnd.android.package-archive",
    "Content-Disposition": `attachment; filename="${apkName}"`,
    "Content-Length": fs.statSync(apkPath).size,
  });
  fs.createReadStream(apkPath).pipe(res);
});

server.listen(port, "0.0.0.0", () => {
  console.log(`Serving ${apkName} on http://0.0.0.0:${port}/`);
});
