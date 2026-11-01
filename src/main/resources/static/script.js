import { api, money } from "./api.js";

const toggle = document.querySelector(".menu-toggle");
const nav = document.querySelector("#primary-nav");
function closeMenu() {
  toggle.setAttribute("aria-expanded", "false");
  toggle.setAttribute("aria-label", "Открыть меню");
  nav.classList.remove("nav-open");
}
toggle.addEventListener("click", () => {
  const open = toggle.getAttribute("aria-expanded") !== "true";
  toggle.setAttribute("aria-expanded", String(open));
  toggle.setAttribute("aria-label", open ? "Закрыть меню" : "Открыть меню");
  nav.classList.toggle("nav-open", open);
});
nav.addEventListener("click", (event) => {
  if (event.target.closest("a")) closeMenu();
});
document.addEventListener("keydown", (event) => {
  if (
    event.key === "Escape" &&
    toggle.getAttribute("aria-expanded") === "true"
  ) {
    closeMenu();
    toggle.focus();
  }
});
document.addEventListener("click", (event) => {
  if (!event.target.closest(".site-header")) closeMenu();
});
matchMedia("(min-width: 801px)").addEventListener("change", closeMenu);

const estimateForm = document.querySelector("#estimate-form");
const estimateStatus = document.querySelector("#estimate-status");
const estimatePrice = document.querySelector("#estimate-price");
const useEstimate = document.querySelector("#use-estimate");
let estimate;
estimateForm.addEventListener("input", () => {
  estimate = undefined;
  useEstimate.hidden = true;
  estimatePrice.textContent = "Параметры изменились";
  estimateStatus.textContent = "Нажмите «Рассчитать стоимость».";
});
estimateForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const button = estimateForm.querySelector("button");
  const fields = [...estimateForm.elements];
  const body = {
    service: estimateForm.elements.service.value,
    area: Number(estimateForm.elements.area.value),
  };
  fields.forEach((field) => {
    field.disabled = true;
  });
  estimateStatus.textContent = "Рассчитываем…";
  try {
    estimate = await api("/api/estimates", { method: "POST", body });
    estimatePrice.textContent = `${money(estimate.min)} — ${money(estimate.max)}`;
    estimateStatus.textContent = `${estimate.area} м² · стоимость работ · тариф ${estimate.pricingVersion}`;
    useEstimate.hidden = false;
  } catch (error) {
    estimatePrice.textContent = "Расчёт недоступен";
    estimateStatus.textContent = error.message;
  } finally {
    fields.forEach((field) => {
      field.disabled = false;
    });
    button.focus({ preventScroll: true });
  }
});
const form = document.querySelector("#project-form");
useEstimate.addEventListener("click", () => {
  if (!estimate) return;
  form.elements.service.value = estimate.service;
  form.elements.area.value = estimate.area;
});

// Keep the same ID for an identical retry after a lost network response; no contacts in storage.
let pendingPayload;
let pendingId;
form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const status = document.querySelector("#form-status");
  const body = {
    name: form.elements.name.value.trim(),
    email: form.elements.email.value.trim(),
    service: form.elements.service.value,
    area: Number(form.elements.area.value),
    message: form.elements.message.value.trim(),
    consent: form.elements.consent.checked,
  };
  if (body.name.length < 2) {
    status.textContent = "Укажите имя минимум из двух символов.";
    form.elements.name.focus();
    return;
  }
  const serialized = JSON.stringify(body);
  if (serialized !== pendingPayload) {
    pendingId = crypto.randomUUID();
    pendingPayload = serialized;
  }
  const fields = [...form.elements];
  fields.forEach((field) => {
    field.disabled = true;
  });
  form.setAttribute("aria-busy", "true");
  status.classList.remove("is-error");
  status.textContent = "Сохраняем бриф…";
  try {
    const receipt = await api("/api/inquiries", {
      method: "POST",
      body: { ...body, requestId: pendingId },
    });
    status.textContent = `Бриф сохранён. Номер: ${receipt.id}. Ориентир: ${money(receipt.estimateMin)} — ${money(receipt.estimateMax)}. Это демопроект, обратного звонка не будет.`;
    form.reset();
    pendingPayload = pendingId = undefined;
  } catch (error) {
    status.classList.add("is-error");
    status.textContent = error.message;
  } finally {
    fields.forEach((field) => {
      field.disabled = false;
    });
    form.removeAttribute("aria-busy");
  }
});
