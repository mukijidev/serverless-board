"use strict";

// ---------- Configuration ----------
let config = null;

async function loadConfig() {
  const response = await fetch("config.json");
  if (!response.ok) {
    throw new Error("Could not load config.json");
  }
  config = await response.json();
}

// ---------- Small DOM helpers ----------
const el = (id) => document.getElementById(id);

function showMessage(text, type = "") {
  const message = el("message");
  message.textContent = text;
  message.className = type;
}

// ---------- Session: the ID token lives in sessionStorage ----------
const TOKEN_KEY = "idToken";

function saveToken(token) {
  sessionStorage.setItem(TOKEN_KEY, token);
}

function getToken() {
  return sessionStorage.getItem(TOKEN_KEY);
}

function clearToken() {
  sessionStorage.removeItem(TOKEN_KEY);
}

function decodeJwtPayload(token) {
  const base64 = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
  return JSON.parse(atob(base64));
}

function currentUser() {
  const token = getToken();
  if (!token) {
    return null;
  }
  try {
    const payload = decodeJwtPayload(token);
    if (payload.exp * 1000 < Date.now()) {
      clearToken();
      return null;
    }
    return { id: payload.sub, email: payload.email };
  } catch (error) {
    clearToken();
    return null;
  }
}

// ---------- Screen state ----------
function renderAuthState() {
  const user = currentUser();
  el("auth-section").hidden = Boolean(user);
  el("user-area").hidden = !user;
  el("new-post-section").hidden = !user;
  el("user-label").textContent = user ? user.email : "";
}

// ---------- Cognito (called directly from the browser) ----------
async function cognitoRequest(action, payload) {
  const response = await fetch(
    `https://cognito-idp.${config.region}.amazonaws.com/`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/x-amz-json-1.1",
        "X-Amz-Target": `AWSCognitoIdentityProviderService.${action}`,
      },
      body: JSON.stringify({ ClientId: config.clientId, ...payload }),
    },
  );
  const data = await response.json();
  if (!response.ok) {
    const error = new Error(data.message || "Request failed");
    error.code = data.__type;
    throw error;
  }
  return data;
}

async function login(email, password) {
  const data = await cognitoRequest("InitiateAuth", {
    AuthFlow: "USER_PASSWORD_AUTH",
    AuthParameters: { USERNAME: email, PASSWORD: password },
  });
  saveToken(data.AuthenticationResult.IdToken);
}

function logout() {
  clearToken();
}

async function signUp(email, password) {
  await cognitoRequest("SignUp", { Username: email, Password: password });
}

async function confirmSignUp(email, code) {
  await cognitoRequest("ConfirmSignUp", {
    Username: email,
    ConfirmationCode: code,
  });
}

async function resendCode(email) {
  await cognitoRequest("ResendConfirmationCode", { Username: email });
}

// ---------- API ----------
async function apiRequest(path, { method = "GET", body, auth = false } = {}) {
  const headers = {};
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (auth) {
    const token = getToken();
    if (!token) {
      throw new Error("Please log in first.");
    }
    headers["Authorization"] = token;
  }

  const response = await fetch(config.apiBaseUrl + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 204) {
    return null;
  }
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(data?.message || `Request failed (${response.status})`);
  }
  return data;
}

// ---------- Posts ----------
async function loadPosts() {
  try {
    const posts = await apiRequest("posts");
    renderPosts(posts);
  } catch (error) {
    showMessage(error.message, "error");
  }
}

function renderPosts(posts) {
  const list = el("post-list");
  list.replaceChildren();

  if (posts.length === 0) {
    const empty = document.createElement("li");
    empty.textContent = "No posts yet.";
    list.append(empty);
    return;
  }

  const user = currentUser();

  for (const post of posts) {
    list.append(renderPost(post, user));
  }
}

function renderImages(imageKeys) {
  const wrapper = document.createElement("div");
  wrapper.className = "post-images";
  for (const key of imageKeys) {
    const image = document.createElement("img");
    image.src = `/${key}`;
    image.alt = "Attached Image";
    image.loading = "lazy";
    wrapper.append(image);
  }
  return wrapper;
}

function renderPost(post, user) {
  const item = document.createElement("li");
  item.className = "post";

  const title = document.createElement("h3");
  title.className = "post-title";
  title.textContent = post.title;

  const meta = document.createElement("div");
  meta.className = "post-meta";
  meta.textContent = post.createdAt
    ? new Date(post.createdAt).toLocaleString()
    : "";

  const content = document.createElement("p");
  content.className = "post-content";
  content.textContent = post.content;

  if (post.imageKeys && post.imageKeys.length > 0) {
    item.append(renderImages(post.imageKeys));
  }
  
  item.append(title, meta, content);

  if (user && user.id === post.authorId) {
    const actions = document.createElement("div");
    actions.className = "post-actions";
    actions.append(
      actionButton("Edit", "edit", post.postId, "secondary"),
      actionButton("Delete", "delete", post.postId, "danger"),
    );
    item.append(actions);
  }

  return item;
}

function actionButton(label, action, posotId, extraClass) {
  const button = document.createElement("button");
  button.type = "button";
  button.textContent = label;
  button.className = extraClass;
  button.dataset.action = action;
  button.dataset.postId = posotId;
  return button;
}

async function updatePost(postId, title, content) {
  return apiRequest(`posts/${encodeURIComponent(postId)}`, {
    method: "PUT",
    auth: true,
    body: { title, content },
  });
}

async function deletePost(postId) {
  return apiRequest(`posts/${encodeURIComponent(postId)}`, {
    method: "DELETE",
    auth: true,
  });
}

function startEdit(item, postId) {
  const titleElement = item.querySelector(".post-title");
  const contentElement = item.querySelector(".post-content");

  const titleInput = document.createElement("input");
  titleInput.className = "edit-title";
  titleInput.maxLength = 100;
  titleInput.value = titleElement.textContent;

  const contentInput = document.createElement("textarea");
  contentInput.className = "edit-content";
  contentInput.value = contentElement.textContent;

  titleElement.replaceWith(titleInput);
  contentElement.replaceWith(contentInput);

  item
    .querySelector(".post-actions")
    .replaceChildren(
      actionButton("Save", "save", postId, ""),
      actionButton("Cancel", "cancel", postId, "secondary"),
    );
}

async function handleSave(item, postId, button) {
  const title = item.querySelector(".edit-title").value.trim();
  const content = item.querySelector(".edit-content").value.trim();
  if (!title || !content) {
    showMessage("Title and content must not be empty.", "error");
    return;
  }
  button.disabled = true;
  try {
    await updatePost(postId, title, content);
    showMessage("Post updated.", "success");
    await loadPosts();
  } catch (error) {
    showMessage(error.message, "error");
    button.disabled = false;
  }
}

async function handleDelete(postId, button) {
  if (!confirm("Delete this post?")) {
    return;
  }
  button.disabled = true;
  try {
    await deletePost(postId);
    showMessage("Post deleted.", "success");
    await loadPosts();
  } catch (error) {
    showMessage(error.message, "error");
    button.disabled = false;
  }
}

const MAX_IMAGES = 5;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const ALLOWED_TYPES = ["image/jpeg", "image/png"];

function validateImages(files) {
  if (files.length > MAX_IMAGES) {
    return `You can attach up to ${MAX_IMAGES} images.`;
  }
  for (const file of files) {
    if (!ALLOWED_TYPES.includes(file.type)) {
      return "Only JPEG and PNG images are allowed.";
    }
    if (file.size > MAX_IMAGE_BYTES) {
      return "Each image must be 5 MB or smaller.";
    }
  }
  return null;
}

async function uploadImages(files) {
  const keys = [];
  for (const file of files) {
    const upload = await apiRequest("uploads", {
      method: "POST",
      auth: true,
      body: { contentType: file.type, size: file.size },
    });
    const response = await fetch(upload.uploadUrl, {
      method: "PUT",
      headers: { "Content-Type": file.type },
      body: file,
    });
    if (!response.ok) {
      throw new Error(`Image upload failed (${response.status})`);
    }
    keys.push(upload.key);
  }
  return keys;
}

async function createPost(title, content, files) {
  const imageKeys = files.length > 0 ? await uploadImages(files) : [];
  return apiRequest("posts", {
    method: "POST",
    auth: true,
    body: { title, content, imageKeys },
  });
}

// ---------- Events ----------
let pendingEmail = null;

function setBusy(form, busy) {
  form.querySelectorAll("button").forEach((button) => {
    button.disabled = busy;
  });
}

function showConfirmForm(email) {
  pendingEmail = email;
  el("confirm-form").hidden = false;
}

function bindEvents() {
  el("login-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const email = el("login-email").value;
    setBusy(form, true);
    try {
      await login(email, el("login-password").value);
      form.reset();
      showMessage("Logged in.", "success");
      renderAuthState();
      await loadPosts();
    } catch (error) {
      if (error.code === "UserNotConfirmedException") {
        showConfirmForm(email);
        showMessage(
          "Your account is not confirmed yet. Enter the code sent to your email.",
          "error",
        );
      } else {
        showMessage(error.message, "error");
      }
    } finally {
      setBusy(form, false);
    }
  });

  el("signup-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const email = el("signup-email").value;
    setBusy(form, true);
    try {
      await signUp(email, el("signup-password").value);
      form.reset();
      showConfirmForm(email);
      showMessage(
        "Account created. Enter the code sent to your email.",
        "success",
      );
    } catch (error) {
      showMessage(error.message, "error");
    } finally {
      setBusy(form, false);
    }
  });

  el("confirm-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    setBusy(form, true);
    try {
      await confirmSignUp(pendingEmail, el("confirm-code").value.trim());
      form.reset();
      form.hidden = true;
      showMessage("Account confirmed. You can log in now.", "success");
    } catch (error) {
      showMessage(error.message, "error");
    } finally {
      setBusy(form, false);
    }
  });

  el("resend-button").addEventListener("click", async () => {
    try {
      await resendCode(pendingEmail);
      showMessage("A new code was sent to your email.", "success");
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  el("post-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const files = Array.from(el("post-images").files);

    const imageError = validateImages(files);
    if (imageError) {
      showMessage(imageError, "error");
      return;
    }

    setBusy(form, true);
    try {
      await createPost(
        el("post-title").value.trim(),
        el("post-content").value.trim(),
        files,
      );
      form.reset();
      showMessage("Post created.", "success");
      await loadPosts();
    } catch (error) {
      showMessage(error.message, "error");
    } finally {
      setBusy(form, false);
    }
  });

  el("post-list").addEventListener("click", async (event) => {
    const button = event.target.closest("button[data-action]");
    if (!button) {
      return;
    }
    const { action, postId } = button.dataset;
    const item = button.closest(".post");

    if (action === "edit") {
      startEdit(item, postId);
    } else if (action === "save") {
      await handleSave(item, postId, button);
    } else if (action === "cancel") {
      await loadPosts();
    } else if (action === "delete") {
      await handleDelete(postId, button);
    }
  });

  el("logout-button").addEventListener("click", () => {
    logout();
    renderAuthState();
    loadPosts();
    showMessage("Logged out.", "success");
  });
}

// ---------- Start ----------
async function init() {
  try {
    await loadConfig();
  } catch (error) {
    showMessage(error.message, "error");
    return;
  }
  renderAuthState();
  bindEvents();
  loadPosts();
}

init();
