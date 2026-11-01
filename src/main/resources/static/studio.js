import { api, csrfToken, money } from "./api.js";

const labels = {
  NEW: "Новая",
  REVIEWING: "В работе",
  CONTACTED: "Связались",
  CLOSED: "Закрыта",
};
const services = {
  ARCHITECTURE: "Архитектурная концепция",
  INTERIOR: "Дизайн интерьера",
  SUPERVISION: "Авторское сопровождение",
};
const transitions = {
  NEW: ["REVIEWING", "CLOSED"],
  REVIEWING: ["CONTACTED", "CLOSED"],
  CONTACTED: ["CLOSED"],
  CLOSED: [],
};
const actionLabels = {
  REVIEWING: "Взять в работу",
  CONTACTED: "Отметить контакт",
  CLOSED: "Закрыть",
};
const list = document.querySelector("#inquiries");
const status = document.querySelector("#studio-status");
const filter = document.querySelector("#status-filter");
let page = 0;
let loading = false;

function node(tag, text, className) {
  const element = document.createElement(tag);
  if (text !== undefined) element.textContent = text;
  if (className) element.className = className;
  return element;
}

function card(inquiry) {
  const article = node("article", undefined, "inquiry-card");
  const meta = node("div", undefined, "inquiry-meta");
  meta.append(
    node("span", labels[inquiry.status], "status-badge"),
    node("time", new Date(inquiry.createdAt).toLocaleString("ru-RU")),
  );
  const email = node("p", inquiry.email);
  // Client input is always rendered as text, including email and the project description.
  article.append(
    meta,
    node("h2", inquiry.name),
    email,
    node("p", `${services[inquiry.service]} · ${inquiry.area} м²`),
    node("p", `${money(inquiry.estimateMin)} — ${money(inquiry.estimateMax)}`),
    node(
      "p",
      inquiry.message || "Без дополнительных пожеланий.",
      "inquiry-message",
    ),
    node("p", `№ ${inquiry.id}`),
  );
  const actions = node("div", undefined, "inquiry-actions");
  for (const next of transitions[inquiry.status]) {
    const button = node("button", actionLabels[next], "button button-dark");
    button.type = "button";
    button.addEventListener("click", async () => {
      actions.querySelectorAll("button").forEach((item) => {
        item.disabled = true;
      });
      try {
        await api(`/api/admin/inquiries/${inquiry.id}/status`, {
          method: "PATCH",
          body: { status: next, version: inquiry.version },
        });
        await load();
        status.textContent = `Статус обновлён: ${labels[next]}.`;
      } catch (error) {
        status.textContent = error.message;
        if (error.status === 401) location.assign("/login.html");
      } finally {
        actions.querySelectorAll("button").forEach((item) => {
          item.disabled = false;
        });
      }
    });
    actions.append(button);
  }
  article.append(actions);
  return article;
}

async function load() {
  if (loading) return;
  loading = true;
  list.setAttribute("aria-busy", "true");
  document.querySelector("#refresh").disabled = true;
  filter.disabled = true;
  document.querySelector("#prev").disabled = true;
  document.querySelector("#next").disabled = true;
  status.textContent = "Загружаем заявки…";
  try {
    const query = new URLSearchParams({ page: String(page) });
    if (filter.value) query.set("status", filter.value);
    const result = await api(`/api/admin/inquiries?${query}`);
    list.replaceChildren(...result.items.map(card));
    if (result.items.length === 0)
      list.append(
        node(
          "p",
          "Здесь пока нет заявок. Новый бриф появится после отправки формы на сайте.",
          "empty-state",
        ),
      );
    document.querySelector("#total-count").textContent =
      `Заявок: ${result.totalElements}`;
    document.querySelector("#page-label").textContent =
      `${result.totalPages ? page + 1 : 0} / ${result.totalPages}`;
    document.querySelector("#prev").disabled = page === 0;
    document.querySelector("#next").disabled = page + 1 >= result.totalPages;
    status.textContent = "";
  } catch (error) {
    status.textContent = error.message;
    if (error.status === 401) location.assign("/login.html");
  } finally {
    loading = false;
    list.removeAttribute("aria-busy");
    document.querySelector("#refresh").disabled = false;
    filter.disabled = false;
  }
}
filter.addEventListener("change", () => {
  page = 0;
  load();
});
document.querySelector("#refresh").addEventListener("click", load);
document.querySelector("#prev").addEventListener("click", () => {
  if (!loading && page > 0) {
    page--;
    load();
  }
});
document.querySelector("#next").addEventListener("click", () => {
  if (!loading) {
    page++;
    load();
  }
});
try {
  const csrf = await csrfToken();
  document.querySelector("#logout-csrf").value = csrf.token;
  document.querySelector("#logout-button").disabled = false;
  await load();
} catch (error) {
  status.textContent = error.message;
}
