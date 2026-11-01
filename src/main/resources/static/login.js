import { csrfToken } from "./api.js";
const status = document.querySelector("#login-status");
if (new URLSearchParams(location.search).has("error"))
  status.textContent = "Не удалось войти. Проверьте логин и пароль.";
try {
  const csrf = await csrfToken();
  document.querySelector("#login-csrf").value = csrf.token;
  document.querySelector("#login-button").disabled = false;
} catch (error) {
  status.textContent = error.message;
}
