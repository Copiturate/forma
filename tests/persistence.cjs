const { request } = require("playwright");
const { spawn } = require("node:child_process");
const { once } = require("node:events");
const fs = require("node:fs/promises");
const path = require("node:path");
const os = require("node:os");
const assert = require("node:assert/strict");
const { randomUUID } = require("node:crypto");

async function main() {
  const directory = await fs.mkdtemp(
    path.join(os.tmpdir(), "forma-persistence-"),
  );
  const port = process.env.FORMA_PERSISTENCE_PORT || "18081";
  const java = process.env.JAVA_HOME
    ? path.join(
        process.env.JAVA_HOME,
        "bin",
        process.platform === "win32" ? "java.exe" : "java",
      )
    : "java";
  const jar = path.resolve("target/forma-1.0.0.jar");
  const password = "disposable-persistence-test-2026";
  let child;
  let logs = "";
  async function start() {
    child = spawn(java, ["-jar", jar], {
      env: {
        ...process.env,
        PORT: port,
        FORMA_ADMIN_USERNAME: "persistence-test",
        FORMA_ADMIN_PASSWORD: password,
        FORMA_DB_URL: `jdbc:h2:file:${path.join(directory, "forma").replaceAll("\\", "/")};DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0`,
        FORMA_DB_USERNAME: "sa",
        FORMA_DB_PASSWORD: "",
        FORMA_COOKIE_SECURE: "false",
      },
      windowsHide: true,
      stdio: ["ignore", "pipe", "pipe"],
    });
    child.stdout.on("data", (chunk) => {
      logs = (logs + chunk).slice(-8000);
    });
    child.stderr.on("data", (chunk) => {
      logs = (logs + chunk).slice(-8000);
    });
    const context = await request.newContext({
      baseURL: `http://localhost:${port}`,
    });
    for (let attempt = 0; attempt < 60; attempt++) {
      if (child.exitCode !== null)
        throw new Error(`Application exited: ${logs}`);
      try {
        if (
          logs.includes("Started FormaApplication") &&
          (await context.get("/api/services", { timeout: 1000 })).ok()
        )
          return context;
      } catch {
        /* Startup can take several seconds. */
      }
      await new Promise((resolve) => setTimeout(resolve, 500));
    }
    await context.dispose();
    throw new Error(`Application did not become ready: ${logs}`);
  }
  async function stop() {
    if (child && child.exitCode === null) {
      const exited = once(child, "exit");
      child.kill();
      await exited;
    }
  }
  let context;
  try {
    context = await start();
    const csrf = await (await context.get("/api/csrf")).json();
    const id = randomUUID();
    const response = await context.post("/api/inquiries", {
      headers: { [csrf.headerName]: csrf.token },
      data: {
        requestId: id,
        name: "Проверка хранения",
        email: "persistence@example.com",
        service: "INTERIOR",
        area: 80,
        message: "После перезапуска",
        consent: true,
      },
    });
    assert.equal(response.status(), 201);
    await context.dispose();
    await stop();
    context = await start();
    const loginCsrf = await (await context.get("/api/csrf")).json();
    await context.post("/login", {
      form: { username: "persistence-test", password, _csrf: loginCsrf.token },
    });
    const list = await context.get("/api/admin/inquiries");
    assert.equal(list.status(), 200);
    const body = await list.json();
    assert.equal(body.totalElements, 1);
    assert.equal(body.items[0].id, id);
    assert.equal(body.items[0].message, "После перезапуска");
    assert.equal(body.items[0].estimateMin, 320000);
    console.log(
      "PASS: file database retains the inquiry and its estimate after application restart",
    );
  } finally {
    await context?.dispose();
    await stop();
    // Only remove this exact generated test directory under the OS temporary directory.
    if (
      path.dirname(directory) !== path.resolve(os.tmpdir()) ||
      !path.basename(directory).startsWith("forma-persistence-")
    ) {
      throw new Error("Unexpected temporary directory; cleanup refused");
    }
    await fs.rm(directory, {
      recursive: true,
      force: true,
      maxRetries: 5,
      retryDelay: 200,
    });
  }
}
main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
