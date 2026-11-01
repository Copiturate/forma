let csrf;

export async function csrfToken() {
  const response = await fetch("/api/csrf", {
    credentials: "same-origin",
    cache: "no-store",
  });
  if (!response.ok)
    throw new Error("Не удалось установить соединение. Обновите страницу.");
  csrf = await response.json();
  return csrf;
}

export async function api(path, { method = "GET", body } = {}) {
  const headers = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (method !== "GET") {
    const token = csrf || (await csrfToken());
    headers[token.headerName] = token.token;
  }
  let response;
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: "same-origin",
    });
  } catch {
    throw new Error(
      "Сервер недоступен. Проверьте соединение и повторите попытку.",
    );
  }
  if (response.status === 401) {
    const error = new Error("Сессия истекла. Войдите в кабинет снова.");
    error.status = 401;
    throw error;
  }
  if (response.status === 403) {
    csrf = undefined;
    throw new Error("Сессия обновилась. Повторите отправку.");
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    const error = new Error(
      problem.detail ||
        (response.status === 429
          ? "Слишком много запросов. Подождите минуту."
          : "Запрос не выполнен. Попробуйте ещё раз."),
    );
    error.status = response.status;
    throw error;
  }
  return response.json();
}

export const money = (value) =>
  new Intl.NumberFormat("ru-RU", {
    style: "currency",
    currency: "RUB",
    maximumFractionDigits: 0,
  }).format(value);
