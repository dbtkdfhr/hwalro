import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

const script = process.platform === "win32" ? "migrate-auth-roles.ps1" : "migrate-auth-roles.sh";
const scriptPath = fileURLToPath(new URL(`./${script}`, import.meta.url));
const command = process.platform === "win32" ? "powershell.exe" : "bash";
const args =
  process.platform === "win32" ? ["-NoProfile", "-ExecutionPolicy", "Bypass", "-File", scriptPath] : [scriptPath];
const retry = process.argv.includes("--retry");
const maxAttempts = retry ? 60 : 1;
let attempt = 0;

function run() {
  attempt += 1;
  const child = spawn(command, args, { stdio: "inherit" });

  child.on("error", (error) => {
    console.error(`Failed to start ${command}: ${error.message}`);
    process.exitCode = 1;
  });

  child.on("exit", (code) => {
    if (code === 0) {
      if (retry) {
        console.log("\n========== SUCCESS ==========");
      }
      return;
    }

    if (code !== 0 && attempt < maxAttempts) {
      console.error(`MySQL is not ready; retrying role migration (${attempt}/${maxAttempts})...`);
      setTimeout(run, 1000);
      return;
    }

    process.exitCode = code ?? 1;
  });
}

run();
