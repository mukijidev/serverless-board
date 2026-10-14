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



async function signUp(email, password, nickname) {
  await cognitoRequest("SignUp",
     { Username: email,
       Password: password,
       UserAttributes: [{Name: "nickname", Value: nickname}],
      });
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

function renderContent(text, imageKeys) {
  const container = document.createElement("div");
  container.className = "post-content";
  container.dataset.raw = text;

  const used = new Set();
  const parts = text.split(/\[\[image:(\d+)\]\]/);

  parts.forEach((part, i) => {
    if(i%2 === 0) {
      container.append(part);
      return;
    }

    const number = Number(part);
    const key = imageKeys[number - 1];
    if(!key) {
      container.append(imageMarker(number));
      return;
    }

    const image = document.createElement("img");
    image.className = "inline-image";
    image.src = `/${key}`;
    image.alt = "Attached image";
    image.loading = "lazy";
    container.append(image);
    used.add(number - 1);
  });

  return {container, used};
}

function renderPost(post, user) {
  const item = document.createElement("li");
  item.className = "post";
  const isMine = Boolean(user) && user.id === post.authorId;

  // click to expand
  const row = document.createElement("button");
  row.type = "button";
  row.className = "post-row";
  row.dataset.action = "toggle-detail";
  row.dataset.postId = post.postId;
  row.dataset.title = post.title;
  
  const thumb = document.createElement("span");
  thumb.className = "row-thumb";

  const thumbnailKey = post.thumbnailKey ?? post.imageKeys?.[0];
  if (thumbnailKey) {
    const image = document.createElement("img");
    image.src = `/${thumbnailKey}`;
    image.alt = "";
    image.loading = "lazy";
    thumb.append(image);
  }



  const rowTitle = document.createElement("span");
  rowTitle.className = "row-title";
  rowTitle.textContent = `${post.title} [${post.commentCount ?? 0}]`;

  const rowAuthor = document.createElement("span");
  rowAuthor.className = "row-author";
  rowAuthor.classList.toggle("mine", isMine);
  rowAuthor.textContent = post.authorName ?? "Unknown";

  const rowMeta = document.createElement("span");
  rowMeta.className = "row-meta";
  rowMeta.textContent = post.createdAt ? new Date(post.createdAt).toLocaleDateString() : "";

  row.append(thumb, rowTitle, rowAuthor, rowMeta);


  //hidden until the row is clicked
  const detail = document.createElement("div");
  detail.className = "post-detail";
  detail.hidden = true;

  const meta = document.createElement("div");
  meta.className = "post-meta";
  meta.textContent = post.createdAt ? new Date(post.createdAt).toLocaleString() : "";

  const imageKeys = post.imageKeys ?? [];
  const { container : content, used } = renderContent(post.content, imageKeys);

  detail.append(meta, content);

  const unusedKeys = imageKeys.filter((key, index) => !used.has(index));
  if(unusedKeys.length > 0) {
    detail.append(renderImages(unusedKeys));
  }

  if (isMine) {
    const actions = document.createElement("div");
    actions.className = "post-actions";
    actions.append(
      actionButton("Edit", "edit", post.postId, "secondary"),
      actionButton("Delete", "delete", post.postId, "danger")
    );
    detail.append(actions);
  }

  const toggle = actionButton(
    `Comments (${post.commentCount ?? 0})`,
    "toggle-comments",
    post.postId,
    "secondary"
  );

  const panel = document.createElement("div");
  panel.className = "comments";
  panel.hidden = true;

  const commentList = document.createElement("ul");
  commentList.className = "comment-list";
  panel.append(commentList);

  if (user) {
    panel.append(renderCommentForm(post.postId));
  }

  detail.append(toggle, panel);
  item.append(row, detail);

  return item;
}

function renderComments(panel, postId, comments, user) {
  const list = panel.querySelector(".comment-list");
  list.replaceChildren();

  if (comments.length === 0) {
    const empty = document.createElement("li");
    empty.className = "comment-empty";
    empty.textContent = "No comments yet.";
    list.append(empty);
    return;
  }

  for (const comment of comments) {
    list.append(renderComment(comment, postId, user));
  }
}

function renderComment(comment, postId, user) {
  const item = document.createElement("li");
  item.className = "comment";

  const text = document.createElement("p");
  text.className = "comment-text";
  text.textContent = comment.content;

  const meta = document.createElement("span");
  meta.className = "comment-meta";
  meta.textContent = new Date(comment.createdAt).toLocaleString();

  item.append(text, meta);

  if (user && user.id === comment.authorId) {
    const remove = actionButton("Delete", "delete-comment", postId, "danger");
    remove.dataset.commentId = comment.commentId;
    item.append(remove);
  }

  return item;
}

const MAX_COMMENT_LENGTH = 500;

function renderCommentForm(postId) {
  const form = document.createElement("div");
  form.className = "comment-form";

  const input = document.createElement("textarea");
  input.className = "comment-input";
  input.maxLength = MAX_COMMENT_LENGTH;
  input.rows = 2;
  input.placeholder = "Write a comment";

  form.append(input, actionButton("Add comment", "add-comment", postId, ""));
  return form;
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

async function getComments(postId) {
  return apiRequest(`posts/${encodeURIComponent(postId)}/comments`);
}

async function addComment(postId, content) {
  return apiRequest(`posts/${encodeURIComponent(postId)}/comments`, {
    method: "POST",
    auth: true,
    body: { content },
  });
}

async function deleteComment(postId, commentId) {
  return apiRequest(
    `posts/${encodeURIComponent(postId)}/comments/${encodeURIComponent(commentId)}`,
    { method: "DELETE", auth: true },
  );
}



function startEdit(item, postId) {
  const detail = item.querySelector(".post-detail");
  const row = item.querySelector(".post-row");
  const contentElement = item.querySelector(".post-content");


  const titleInput = document.createElement("input");
  titleInput.className = "edit-title";
  titleInput.maxLength = 100;
  titleInput.value = row.dataset.title;

  const contentInput = document.createElement("textarea");
  contentInput.className = "edit-content";
  contentInput.value = contentElement.dataset.raw;

  detail.prepend(titleInput);
  contentElement.replaceWith(contentInput);

  item.querySelector(".post-actions")
  .replaceChildren (
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

async function refreshComments(item, postId) {
  const panel = item.querySelector(".comments");
  const comments = await getComments(postId);
  renderComments(panel, postId, comments, currentUser());
  item.querySelector("button[data-action='toggle-comments']").textContent = `Comments (${comments.length})`;
}


async function toggleComments(item, postId, button) {
  const panel = item.querySelector(".comments");
  if (!panel.hidden) {
    panel.hidden = true;
    return;
  }

  button.disabled = true;
  try {
    await refreshComments(item, postId);
    panel.hidden = false;

  } catch(error) {
    showMessage(error.message, "error");
  } finally {
    button.disabled = false;
  }
}

async function handleAddComment(item, postId, button) {
  const input = item.querySelector(".comment-input");
  const content = input.value.trim();
  if (!content) {
    showMessage("Comment must not be empty.", "error");
    return;
  }

  button.disabled = true;
  try {
    await addComment(postId, content);
    input.value = "";
    await refreshComments(item, postId);
    showMessage("Comment added.", "success");
  } catch (error) {
    showMessage(error.message, "error");
  } finally {
    button.disabled = false;
  }
}

async function handleDeleteComment(item, postId, commentId, button) {
  if (!confirm("Delete this comment?")) {
    return;

  }

  button.disabled = true;
  try {
    await deleteComment(postId, commentId);
    await refreshComments(item, postId);
    showMessage("Comment deleted.", "success");
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

async function createPost(title, content, files, thumbnailIndex) {
  const imageKeys = files.length > 0 ? await uploadImages(files) : [];
  const thumbnailKey = imageKeys[thumbnailIndex] ?? null;
  return apiRequest("posts", {
    method: "POST",
    auth: true,
    body: {title, content, imageKeys, thumbnailKey},
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

let thumbnailIndex = 0;

function imageMarker(number) {
  return `[[image:${number}]]`;
}

function updateInsertButtons() {
  const text = el("post-content").value;
  el("insert-buttons").querySelectorAll("button").forEach((button) => {
    const marker = imageMarker(Number(button.dataset.index) + 1);
    button.disabled = text.includes(marker);
  });
}


function renderPreviews() {
  const container = el("image-previews");
  container.replaceChildren();

  const files = Array.from(el("post-images").files);
  el("preview-hint").hidden = files.length === 0;

  const insertButtons = el("insert-buttons");
  insertButtons.replaceChildren();
  
  files.forEach((file, index) => {
    const image = document.createElement("img");
    image.src = URL.createObjectURL(file);
    image.alt = file.name;
    image.dataset.index = index;
    image.classList.toggle("selected", index === thumbnailIndex);
    container.append(image);

    const insertButton = document.createElement("button");
    insertButton.type = "button";
    insertButton.className = "secondary";
    insertButton.textContent = `Insert image ${index + 1}`;
    insertButton.dataset.index = index;
    insertButtons.append(insertButton);
  });

  updateInsertButtons();
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
      const nickname = el("signup-nickname").value.trim();
      if(nickname.length < 2) {
        showMessage("Nickname must be at least 2 characvters.", "error");
        return;
      }
      
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

  el("post-images").addEventListener("change", () => {
    thumbnailIndex = 0;
    renderPreviews();
  });

  el("post-content").addEventListener("input", updateInsertButtons);

  el("image-previews").addEventListener("click", (event) => {
    const image = event.target.closest("img[data-index]");
    if(!image) {
      return;
    }

    thumbnailIndex = Number(image.dataset.index);
    el("image-previews").querySelectorAll("img").forEach((preview) => {
      preview.classList.toggle("selected", preview === image);
    });
  });

  el("insert-buttons").addEventListener("click", (event) => {
    const button = event.target.closest("button[data-index]");
    if (!button) {
      return;
    }

    const textarea = el("post-content");
    const marker = imageMarker(Number(button.dataset.index) + 1);
    textarea.setRangeText(marker, textarea.selectionStart, textarea.selectionEnd, "end");
    textarea.focus();
    updateInsertButtons();
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
        thumbnailIndex,
      );
      form.reset();
      renderPreviews();
      showMessage("Post created.", "success");
      await loadPosts();
    } catch (error) {
      showMessage(error.message, "error");
    } finally {
      setBusy(form, false);
      updateInsertButtons();
    }
  });

  el("post-list").addEventListener("click", async (event) => {
    const button = event.target.closest("button[data-action]");
    if (!button) {
      return;
    }
    const { action, postId, commentId } = button.dataset;
    const item = button.closest(".post");

    if (action === "edit") {
      startEdit(item, postId);
    } else if (action === "save") {
      await handleSave(item, postId, button);
    } else if (action === "cancel") {
      await loadPosts();
    } else if (action === "delete") {
      await handleDelete(postId, button);
    } else if (action === "toggle-detail") {
      const detail = item.querySelector(".post-detail");
      detail.hidden = !detail.hidden;
    } else if (action === "toggle-comments") {
      await toggleComments(item, postId, button);
    } else if (action === "add-comment") {
      await handleAddComment(item, postId, button);
    } else if (action === "delete-comment") {
      await handleDeleteComment(item, postId, commentId, button);
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
