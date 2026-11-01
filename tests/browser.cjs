const { chromium } = require("playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs/promises");

// Run against a disposable database: this test creates one inquiry and closes it.
async function main() {
  const base = process.env.FORMA_TEST_URL || "http://localhost:8080";
  const password = process.env.FORMA_ADMIN_PASSWORD;
  assert.ok(
    password,
    "Set FORMA_ADMIN_PASSWORD to the same password as the running server",
  );
  const browser = await chromium.launch({ headless: true });
  const errors = [];
  const page = await browser.newPage({
    viewport: { width: 1440, height: 1000 },
  });
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  try {
    await fs.mkdir("docs/screenshots", { recursive: true });
    await page.goto(base);
    await page.evaluate(() => document.fonts.ready);
    assert.equal(await page.title(), "FORMA — архитектура пространства");
    await page.screenshot({
      path: "docs/screenshots/desktop.png",
      fullPage: true,
    });
    await page.screenshot({ path: "docs/screenshots/overview.png" });
    await page.locator("#estimate-area").fill("80");
    await page.locator("#estimate-form button").click();
    await page.waitForFunction(() =>
      document.querySelector("#estimate-price").textContent.includes("320"),
    );
    await page.locator("#use-estimate").click();
    assert.equal(await page.locator("#project-type").inputValue(), "INTERIOR");
    assert.equal(await page.locator("#project-area").inputValue(), "80");
    await page.locator("#client-name").fill("Проверка браузера");
    await page.locator("#client-email").fill("browser@example.com");
    await page
      .locator("#project-message")
      .fill("<img src=x onerror=alert(1)> Проверка безопасного вывода.");
    await page.locator("[name=consent]").check();
    await page.locator("#project-form button").click();
    await page.waitForFunction(() =>
      document
        .querySelector("#form-status")
        .textContent.includes("Бриф сохранён"),
    );
    assert.equal(await page.locator("#client-name").inputValue(), "");

    for (const width of [360, 390, 768, 1024, 1440]) {
      await page.setViewportSize({ width, height: 900 });
      assert.ok(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
        `No horizontal overflow at ${width}px`,
      );
    }
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(base);
    await page.locator(".menu-toggle").click();
    assert.equal(
      await page.locator(".menu-toggle").getAttribute("aria-expanded"),
      "true",
    );
    await page.keyboard.press("Escape");
    assert.equal(
      await page.locator(".menu-toggle").getAttribute("aria-expanded"),
      "false",
    );
    await page.screenshot({
      path: "docs/screenshots/mobile.png",
      fullPage: true,
    });

    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.goto(`${base}/studio.html`);
    await page.waitForURL("**/login.html");
    await page
      .locator("#username")
      .fill(process.env.FORMA_ADMIN_USERNAME || "studio");
    await page.locator("#password").fill(password);
    await page.locator("#login-button").click();
    await page.waitForURL("**/studio.html");
    await page
      .getByRole("heading", { name: "Проверка браузера", exact: true })
      .waitFor();
    const card = page
      .locator(".inquiry-card")
      .filter({
        has: page.getByRole("heading", {
          name: "Проверка браузера",
          exact: true,
        }),
      })
      .first();
    assert.equal(
      await card.locator("img").count(),
      0,
      "Stored input must render as text",
    );
    await card.getByRole("button", { name: "Взять в работу" }).click();
    await page.waitForFunction(() =>
      document
        .querySelector("#studio-status")
        .textContent.includes("Статус обновлён"),
    );
    await page.screenshot({
      path: "docs/screenshots/studio.png",
      fullPage: true,
    });
    await card.getByRole("button", { name: "Отметить контакт" }).click();
    await page.waitForFunction(() =>
      document
        .querySelector("#studio-status")
        .textContent.includes("Связались"),
    );
    await card.getByRole("button", { name: "Закрыть", exact: true }).click();
    await page.waitForFunction(() =>
      document.querySelector("#studio-status").textContent.includes("Закрыта"),
    );
    await page.locator("#status-filter").selectOption("NEW");
    await page.waitForFunction(
      () => !document.querySelector("#inquiries").hasAttribute("aria-busy"),
    );
    assert.equal(
      await page
        .getByRole("heading", { name: "Проверка браузера", exact: true })
        .count(),
      0,
    );
    await page.locator("#logout-button").click();
    await page.waitForURL(`${base}/`);
    const response = await page.request.get(`${base}/api/admin/inquiries`);
    assert.equal(response.status(), 401);
    assert.deepEqual(errors, [], "No browser runtime or console errors");
    console.log(
      "PASS: estimate, persisted inquiry, responsive widths, mobile menu, login, safe text rendering, status lifecycle, filtering, logout",
    );
  } finally {
    await browser.close();
  }
}
main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
