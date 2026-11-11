"use strict";

(() => {
  const byId = (id) => document.getElementById(id);
  const ui = {
    connectionStatus: byId("connection-status"),
    connectionError: byId("connection-error"),
    filesState: byId("files-state"),
    filesList: byId("files-list"),
    breadcrumbs: byId("breadcrumbs"),
    createFolder: byId("create-folder"),
    refreshFiles: byId("refresh-files"),
    uploadPath: byId("upload-path"),
    dropZone: byId("drop-zone"),
    fileInput: byId("file-input"),
    chooseFiles: byId("choose-files"),
    startUploads: byId("start-uploads"),
    cancelUpload: byId("cancel-upload"),
    clearPending: byId("clear-pending"),
    clearFinished: byId("clear-finished"),
    queueHint: byId("queue-hint"),
    queueSummary: byId("queue-summary"),
    queueEmpty: byId("queue-empty"),
    queueList: byId("queue-list"),
    announcement: byId("announcement"),
    dialog: byId("action-dialog"),
    form: byId("action-form"),
    dialogTitle: byId("dialog-title"),
    dialogDescription: byId("dialog-description"),
    dialogFields: byId("dialog-fields"),
    dialogError: byId("dialog-error"),
    dialogCancel: byId("dialog-cancel"),
    dialogSubmit: byId("dialog-submit"),
  };

  const taskStorageKey = "zeroplayer-upload-tasks-v1";
  const terminalStates = new Set(["completed", "failed", "canceled", "unknown"]);
  const queue = [];
  let nextId = 0;
  let currentPath = "";
  let pathReady = false;
  let configLoaded = false;
  let filesLoading = false;
  let filesSequence = 0;
  let busy = false;
  let activeRequest = null;
  let activeCancelable = false;
  let dialogAction = null;
  let dialogPending = false;
  let dialogGeneration = 0;
  let dialogTrigger = null;

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  function announce(message) {
    ui.announcement.textContent = message;
  }

  function setConnection(connected) {
    ui.connectionStatus.dataset.connected = String(connected);
    ui.connectionStatus.textContent = connected ? "已连接" : "连接中断";
    ui.connectionError.hidden = connected;
  }

  function url(path, parameters) {
    if (!parameters) return path;
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(parameters)) query.set(key, String(value));
    return `${path}?${query.toString()}`;
  }

  function parseJson(text) {
    try {
      return JSON.parse(text);
    } catch {
      return null;
    }
  }

  function serverError(body, status) {
    if (body && typeof body.error === "string" && body.error.trim()) return body.error;
    return `设备返回 HTTP ${status}`;
  }

  async function api(path, parameters, method = "GET") {
    const controller = new AbortController();
    const timer = window.setTimeout(() => controller.abort(), 15000);
    let response;
    let text;
    try {
      response = await fetch(url(path, parameters), {
        method,
        cache: "no-store",
        signal: controller.signal,
      });
      text = await response.text();
      setConnection(true);
    } catch (error) {
      setConnection(false);
      if (error.name === "AbortError") throw new Error("连接超时，请检查手机服务和网络");
      throw new Error("无法连接文件服务，请检查手机服务和网络");
    } finally {
      window.clearTimeout(timer);
    }
    const body = parseJson(text);
    if (!response.ok || (body && typeof body.error === "string" && body.error)) {
      throw new Error(serverError(body, response.status));
    }
    if (!body || typeof body !== "object") throw new Error("设备返回了无法识别的数据");
    return body;
  }

  function formatBytes(input) {
    const bytes = Number(input);
    if (!Number.isFinite(bytes) || bytes < 0) return "大小未知";
    if (bytes < 1024) return `${bytes} B`;
    const units = ["KiB", "MiB", "GiB", "TiB", "PiB"];
    let value = bytes / 1024;
    let index = 0;
    while (value >= 1024 && index < units.length - 1) {
      value /= 1024;
      index += 1;
    }
    return `${value.toFixed(value >= 10 ? 1 : 2)} ${units[index]}`;
  }

  function formatDate(value) {
    if (value === null || value === undefined || value === "") return "修改时间未知";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "修改时间未知";
    return `修改于 ${date.toLocaleString("zh-CN")}`;
  }

  function displayPath(path) {
    return path ? `文件 / ${path.split("/").join(" / ")}` : "文件";
  }

  function parentPath(path) {
    const slash = path.lastIndexOf("/");
    return slash < 0 ? "" : path.slice(0, slash);
  }

  function updateControls() {
    const pending = queue.filter((item) => item.state === "pending").length;
    const finished = queue.some((item) => terminalStates.has(item.state));
    ui.chooseFiles.disabled = !pathReady || filesLoading;
    ui.createFolder.disabled = !pathReady || filesLoading;
    ui.startUploads.disabled = busy || pending === 0;
    ui.cancelUpload.disabled = !activeRequest || !activeCancelable;
    ui.clearPending.disabled = pending === 0;
    ui.clearFinished.disabled = !finished;
    ui.queueEmpty.hidden = queue.length !== 0;
    ui.queueSummary.textContent = queue.length
      ? `共 ${queue.length} 个文件，待上传 ${pending} 个`
      : "尚未选择文件";
  }

  function renderBreadcrumbs(path) {
    const parts = path ? path.split("/") : [];
    const fragment = document.createDocumentFragment();
    let accumulated = "";
    const entries = [{ name: "文件", path: "" }];
    for (const part of parts) {
      accumulated = accumulated ? `${accumulated}/${part}` : part;
      entries.push({ name: part, path: accumulated });
    }
    entries.forEach((entry, index) => {
      const item = element("li");
      const button = element("button", "", entry.name);
      button.type = "button";
      if (index === entries.length - 1) button.setAttribute("aria-current", "page");
      button.addEventListener("click", () => loadFiles(entry.path));
      item.append(button);
      fragment.append(item);
    });
    ui.breadcrumbs.replaceChildren(fragment);
    ui.uploadPath.textContent = displayPath(path);
  }

  function entryAction(label, entry, handler, className = "button button-quiet") {
    const button = element("button", className, label);
    button.type = "button";
    button.setAttribute("aria-label", `${label}：${entry.name}`);
    button.addEventListener("click", handler);
    return button;
  }

  function makeEntry(entry) {
    const item = element("li", "entry-item");
    const info = element("div", "entry-info");
    const name = element(entry.type === "directory" ? "button" : "span", "entry-name");
    if (entry.type === "directory") {
      name.classList.add("entry-name-button");
      name.type = "button";
      name.addEventListener("click", () => loadFiles(entry.path));
    }
    name.append(element("bdi", "", entry.name));
    const meta = element("p", "entry-meta");
    const kind = entry.type === "directory" ? "文件夹" : `文件 · ${formatBytes(entry.size)}`;
    meta.textContent = `${kind} · ${formatDate(entry.modifiedAt)}`;
    info.append(name, meta);

    const actions = element("div", "entry-actions");
    if (entry.type === "file") {
      const download = element("a", "button button-quiet", "下载");
      download.href = url("/api/download", { path: entry.path });
      download.setAttribute("aria-label", `下载：${entry.name}`);
      actions.append(download);
    }
    actions.append(
      entryAction("重命名", entry, () => renameEntry(entry)),
      entryAction("移动", entry, () => moveEntry(entry)),
      entryAction("删除", entry, () => deleteEntry(entry)),
    );
    item.append(info, actions);
    return item;
  }

  function validListing(body) {
    return typeof body.path === "string" && Array.isArray(body.entries) && body.entries.every((entry) =>
      entry && typeof entry.name === "string" && typeof entry.path === "string" &&
      (entry.type === "file" || entry.type === "directory"));
  }

  async function loadFiles(path) {
    const sequence = ++filesSequence;
    filesLoading = true;
    ui.filesState.hidden = false;
    ui.filesState.dataset.error = "false";
    ui.filesState.textContent = "正在读取文件…";
    updateControls();
    try {
      const body = await api("/api/files", { path });
      if (!validListing(body)) throw new Error("设备返回了无法识别的文件列表");
      if (sequence !== filesSequence) return;
      currentPath = body.path;
      pathReady = true;
      renderBreadcrumbs(currentPath);
      const fragment = document.createDocumentFragment();
      body.entries.forEach((entry) => fragment.append(makeEntry(entry)));
      ui.filesList.replaceChildren(fragment);
      ui.filesState.hidden = body.entries.length !== 0;
      ui.filesState.textContent = "此文件夹为空。可以上传文件或新建文件夹。";
    } catch (error) {
      if (sequence !== filesSequence) return;
      ui.filesState.hidden = false;
      ui.filesState.dataset.error = "true";
      const previous = pathReady ? "当前仍显示上次读取的内容。" : "可以刷新重试或返回根文件夹。";
      ui.filesState.textContent = `读取失败：${error.message}。${previous}`;
    } finally {
      if (sequence === filesSequence) {
        filesLoading = false;
        updateControls();
      }
    }
  }

  async function initializeFiles() {
    const sequence = filesSequence;
    ui.filesState.hidden = false;
    ui.filesState.dataset.error = "false";
    ui.filesState.textContent = "正在连接文件服务…";
    try {
      const config = await api("/api/config");
      if (typeof config.initialPath !== "string") throw new Error("设备没有返回有效的初始文件夹");
      configLoaded = true;
      if (sequence !== filesSequence) return;
      await loadFiles(config.initialPath);
    } catch (error) {
      if (sequence !== filesSequence) return;
      ui.filesState.dataset.error = "true";
      ui.filesState.textContent = `连接失败：${error.message}。请刷新重试。`;
    }
  }

  function openDialog(options) {
    if (ui.dialog.open) return null;
    const generation = ++dialogGeneration;
    dialogTrigger = document.activeElement;
    dialogPending = false;
    dialogAction = options.submit;
    ui.dialogTitle.textContent = options.title;
    ui.dialogDescription.textContent = options.description;
    ui.dialogFields.replaceChildren();
    ui.dialogError.textContent = "";
    ui.dialogError.hidden = true;
    ui.dialogSubmit.textContent = options.submitLabel;
    ui.dialogSubmit.className = options.danger ? "button button-danger" : "button button-primary";
    ui.dialogSubmit.disabled = Boolean(options.initiallyDisabled);
    ui.dialogCancel.disabled = false;
    ui.dialog.showModal();
    return generation;
  }

  function nameField(value) {
    const label = element("label", "field-label", "名称");
    const input = element("input", "text-field");
    input.id = "dialog-name";
    input.name = "name";
    input.required = true;
    input.autocomplete = "off";
    input.value = value;
    label.htmlFor = input.id;
    ui.dialogFields.append(label, input);
    input.focus();
    input.select();
    return input;
  }

  function createFolder() {
    const parent = currentPath;
    let input;
    const generation = openDialog({
      title: "新建文件夹",
      description: `将在 ${displayPath(parent)} 中创建文件夹。`,
      submitLabel: "创建文件夹",
      submit: async () => {
        await api("/api/folders", { path: parent, name: input.value }, "POST");
        return "文件夹已创建。";
      },
    });
    if (generation !== null) input = nameField("");
  }

  function renameEntry(entry) {
    let input;
    const generation = openDialog({
      title: "重命名",
      description: `为“${entry.name}”输入新名称。`,
      submitLabel: "保存名称",
      submit: async () => {
        await api("/api/rename", { path: entry.path, name: input.value }, "POST");
        return "名称已更新。";
      },
    });
    if (generation !== null) input = nameField(entry.name);
  }

  function deleteEntry(entry) {
    openDialog({
      title: `删除${entry.type === "directory" ? "文件夹" : "文件"}`,
      description: entry.type === "directory"
        ? `确定删除“${entry.name}”吗？文件夹内的内容也可能被删除，此操作无法撤销。`
        : `确定删除“${entry.name}”吗？此操作无法撤销。`,
      submitLabel: "确认删除",
      danger: true,
      submit: async () => {
        await api("/api/delete", { path: entry.path }, "POST");
        return "已删除。";
      },
    });
  }

  function moveEntry(entry) {
    let destination = null;
    let pickerSequence = 0;
    const generation = openDialog({
      title: "移动",
      description: `选择“${entry.name}”的目标文件夹。`,
      submitLabel: "移动到此处",
      initiallyDisabled: true,
      submit: async () => {
        if (destination === null) throw new Error("请先选择目标文件夹");
        await api("/api/move", { path: entry.path, destination }, "POST");
        return "已移动。";
      },
    });
    if (generation === null) return;

    const current = element("p", "picker-current", "正在读取文件夹…");
    const state = element("p", "picker-state");
    state.setAttribute("role", "status");
    const list = element("ul", "directory-list");
    list.setAttribute("aria-label", "可选的目标文件夹");
    const retry = element("button", "button button-quiet", "重试读取文件夹");
    retry.type = "button";
    retry.hidden = true;
    let requestedPath = currentPath;
    retry.addEventListener("click", () => browse(requestedPath));
    ui.dialogFields.append(current, state, list, retry);

    function directoryButton(name, path) {
      const item = element("li");
      const button = element("button", "directory-link", name);
      button.type = "button";
      button.addEventListener("click", () => browse(path));
      item.append(button);
      return item;
    }

    async function browse(path) {
      const sequence = ++pickerSequence;
      requestedPath = path;
      destination = null;
      ui.dialogSubmit.disabled = true;
      state.textContent = "正在读取文件夹…";
      retry.hidden = true;
      list.replaceChildren();
      try {
        const body = await api("/api/files", { path });
        if (!validListing(body)) throw new Error("设备返回了无法识别的文件列表");
        if (generation !== dialogGeneration || sequence !== pickerSequence) return;
        destination = body.path;
        current.textContent = `目标文件夹：${displayPath(destination)}`;
        const fragment = document.createDocumentFragment();
        if (destination) fragment.append(directoryButton("上一级文件夹", parentPath(destination)));
        const directories = body.entries.filter((item) => item.type === "directory");
        directories.forEach((item) => fragment.append(directoryButton(item.name, item.path)));
        list.replaceChildren(fragment);
        const insideItself = entry.type === "directory" &&
          (destination === entry.path || destination.startsWith(`${entry.path}/`));
        if (insideItself) {
          state.textContent = "不能将文件夹移动到自身或其子文件夹，请选择其他位置。";
        } else if (destination === parentPath(entry.path)) {
          state.textContent = "这已经是当前文件夹，请选择其他位置。";
        } else {
          state.textContent = directories.length ? "选择下方文件夹可继续进入。" : "此处没有子文件夹。";
          ui.dialogSubmit.disabled = false;
        }
      } catch (error) {
        if (generation !== dialogGeneration || sequence !== pickerSequence) return;
        state.textContent = `读取失败：${error.message}。`;
        retry.hidden = false;
      }
    }
    browse(currentPath);
  }

  ui.form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!dialogAction || dialogPending || ui.dialogSubmit.disabled) return;
    dialogPending = true;
    ui.dialogSubmit.disabled = true;
    ui.dialogCancel.disabled = true;
    ui.dialogError.hidden = true;
    try {
      const message = await dialogAction();
      ui.dialog.close();
      announce(message);
      if (pathReady) loadFiles(currentPath);
    } catch (error) {
      ui.dialogError.textContent = error.message || "操作失败，请重试。";
      ui.dialogError.hidden = false;
    } finally {
      dialogPending = false;
      ui.dialogSubmit.disabled = false;
      ui.dialogCancel.disabled = false;
    }
  });
  ui.dialogCancel.addEventListener("click", () => ui.dialog.close());
  ui.dialog.addEventListener("cancel", (event) => {
    if (dialogPending) event.preventDefault();
  });
  ui.dialog.addEventListener("close", () => {
    dialogGeneration += 1;
    dialogAction = null;
    if (dialogTrigger && dialogTrigger.isConnected) dialogTrigger.focus();
    else ui.refreshFiles.focus();
    dialogTrigger = null;
  });

  function saveTasks() {
    const records = queue.filter((item) => item.taskId).map((item) => ({
      taskId: item.taskId,
      name: item.name,
      size: item.size,
      path: item.path,
    }));
    try {
      if (records.length) localStorage.setItem(taskStorageKey, JSON.stringify(records));
      else localStorage.removeItem(taskStorageKey);
    } catch {
      const message = "浏览器无法保存任务记录，刷新后可能无法恢复查询。请保持页面开启，直到任务结束。";
      ui.queueHint.textContent = message;
      announce(message);
    }
  }

  function setState(item, state, label, detail = "") {
    const changed = item.state !== state || item.status.textContent !== label || item.detail.textContent !== detail;
    item.state = state;
    item.element.dataset.state = state;
    item.status.textContent = label;
    item.detail.textContent = detail;
    item.detail.hidden = !detail;
    renderItemActions(item);
    updateControls();
    if (changed) announce(`${item.name}：${label}${detail ? `。${detail}` : ""}`);
  }

  function setProgress(item, loaded, total, label) {
    item.progressRow.hidden = false;
    item.progress.setAttribute("aria-label", `${label}：${item.name}`);
    if (Number.isFinite(total) && total > 0 && Number.isFinite(loaded)) {
      const percent = Math.min(100, Math.max(0, Math.floor((loaded / total) * 100)));
      item.progress.value = percent;
      item.progressLabel.textContent = `${label} ${percent}%`;
    } else {
      item.progress.removeAttribute("value");
      item.progressLabel.textContent = Number.isFinite(loaded) ? `${label} ${formatBytes(loaded)}` : label;
    }
  }

  function makeItem(name, size, path, file, taskId = null) {
    const row = element("li", "queue-item");
    const heading = element("div", "item-heading");
    const nameNode = element("bdi", "item-name", name);
    const sizeNode = element("span", "item-size", formatBytes(size));
    heading.append(nameNode, sizeNode);
    const destination = element("p", "item-detail", `目标：${displayPath(path)}`);
    const status = element("p", "item-status", "等待上传");
    const detail = element("p", "item-detail");
    detail.hidden = true;
    const progressRow = element("div", "progress-row");
    progressRow.hidden = true;
    const progress = element("progress");
    progress.max = 100;
    const progressLabel = element("span", "progress-label");
    progressRow.append(progress, progressLabel);
    const actions = element("div", "item-actions");
    actions.hidden = true;
    row.append(heading, destination, status, detail, progressRow, actions);
    const item = {
      id: ++nextId,
      name,
      size,
      path,
      file,
      taskId,
      state: "pending",
      tracking: false,
      element: row,
      status,
      detail,
      progressRow,
      progress,
      progressLabel,
      actions,
    };
    row.dataset.state = item.state;
    renderItemActions(item);
    return item;
  }

  function queueAction(item, label, handler) {
    const button = element("button", "button button-quiet", label);
    button.type = "button";
    button.setAttribute("aria-label", `${label}：${item.name}`);
    button.addEventListener("click", handler);
    return button;
  }

  function removeItem(item) {
    const index = queue.indexOf(item);
    if (index < 0) return;
    queue.splice(index, 1);
    item.file = null;
    item.element.remove();
    saveTasks();
    updateControls();
  }

  function renderItemActions(item) {
    const buttons = [];
    if (item.state === "pending") {
      buttons.push(queueAction(item, "移除", () => {
        removeItem(item);
        announce(`已从待上传中移除 ${item.name}。`);
      }));
    } else if (item.state === "unknown" && item.taskId && !item.tracking) {
      buttons.push(queueAction(item, "重新查询", () => trackTask(item)));
    } else if (item.state === "failed" && item.taskId && !item.tracking) {
      buttons.push(queueAction(item, "重试保存", () => retryTask(item)));
      const download = element("a", "button button-quiet", "下载已接收文件");
      download.href = `/api/uploads/${encodeURIComponent(item.taskId)}/download`;
      download.setAttribute("download", "");
      download.setAttribute("aria-label", `下载已接收文件：${item.name}`);
      buttons.push(download);
    }
    item.actions.replaceChildren(...buttons);
    item.actions.hidden = buttons.length === 0;
  }

  async function retryTask(item) {
    if (!queue.includes(item) || item.state !== "failed" || !item.taskId || item.tracking) return;
    setState(item, "retrying", "正在请求重试保存");
    try {
      const result = await api(`/api/uploads/${encodeURIComponent(item.taskId)}/retry`, undefined, "POST");
      if (result.taskId !== item.taskId) throw new Error("设备没有返回有效的原任务编号");
      if (queue.includes(item)) trackTask(item);
    } catch (error) {
      if (queue.includes(item)) {
        setState(item, "unknown", "重试状态未知", `重试请求未确认：${error.message}。请重新查询任务状态。`);
      }
    }
  }

  function addFiles(files) {
    if (!pathReady || filesLoading) {
      announce("请先等待文件夹读取完成，再选择文件。");
      return;
    }
    let added = 0;
    for (const file of files) {
      const item = makeItem(file.name, file.size, currentPath, file);
      queue.push(item);
      ui.queueList.append(item.element);
      added += 1;
    }
    updateControls();
    if (added) announce(`已添加 ${added} 个文件${busy ? "，将按顺序继续传输" : "，点击开始上传"}。`);
  }

  function upload(item) {
    return new Promise((resolve) => {
      const request = new XMLHttpRequest();
      let settled = false;
      activeRequest = request;
      activeCancelable = true;
      updateControls();

      function finish(result) {
        if (settled) return;
        settled = true;
        if (activeRequest === request) {
          activeRequest = null;
          activeCancelable = false;
        }
        updateControls();
        resolve(result);
      }

      request.upload.addEventListener("progress", (event) => {
        if (settled) return;
        setProgress(item, event.loaded, event.lengthComputable ? event.total : item.size, "传输进度");
      });
      request.upload.addEventListener("load", () => {
        if (settled) return;
        setProgress(item, 1, 1, "传输进度");
        setState(item, "uploading", "传输完成，等待设备确认接收");
        activeCancelable = false;
        updateControls();
      });
      request.addEventListener("load", () => {
        setConnection(true);
        const body = parseJson(request.responseText);
        if (request.status < 200 || request.status >= 300 || (body && body.error)) {
          finish({ kind: "failed", detail: serverError(body, request.status) });
        } else if (!body || typeof body.taskId !== "string" || !body.taskId) {
          finish({ kind: "unknown", detail: "设备没有返回有效的任务编号，文件可能已被接收。请检查目标文件夹后再重试。" });
        } else {
          finish({ kind: "accepted", taskId: body.taskId });
        }
      });
      request.addEventListener("error", () => {
        setConnection(false);
        finish({ kind: "unknown", detail: "连接中断，无法确认设备是否已接收。请检查目标文件夹后再重试。" });
      });
      request.addEventListener("abort", () => {
        finish({ kind: "canceled", detail: "已请求取消传输。设备可能已接收部分或全部数据，请检查目标文件夹后再重试。" });
      });
      try {
        request.open("POST", url("/api/uploads", { path: item.path, name: item.name }));
        request.setRequestHeader("Content-Type", item.file.type || "application/octet-stream");
        request.send(item.file);
      } catch {
        finish({ kind: "failed", detail: "无法发起上传，请检查浏览器后重试。" });
      }
    });
  }

  async function trackTask(item) {
    if (item.tracking || !item.taskId || !queue.includes(item)) return;
    item.tracking = true;
    setState(item, "importing", "正在查询处理状态");
    try {
      while (queue.includes(item)) {
        const result = await api(`/api/uploads/${encodeURIComponent(item.taskId)}`);
        if (!queue.includes(item)) return;
        const labels = {
          QUEUED: "已接收，等待处理",
          RUNNING: "正在处理文件",
          COMMITTING: "正在保存文件",
          COMPLETED: "已完成",
          FAILED: "处理失败",
          CANCELED: "处理已取消",
        };
        if (!Object.prototype.hasOwnProperty.call(labels, result.state)) {
          throw new Error("设备返回了无法识别的任务状态");
        }
        const detail = typeof result.errorMessage === "string" ? result.errorMessage : "";
        if (result.state === "COMPLETED") {
          setProgress(item, 1, 1, "处理进度");
          setState(item, "completed", labels[result.state], detail);
          if (pathReady && !filesLoading && currentPath === item.path) loadFiles(currentPath);
          return;
        }
        if (result.state === "FAILED" || result.state === "CANCELED") {
          setState(item, result.state === "FAILED" ? "failed" : "canceled", labels[result.state],
            detail || "请检查文件夹中的文件以及手机上的任务状态。");
          if (pathReady && !filesLoading && currentPath === item.path) loadFiles(currentPath);
          return;
        }
        const copied = Number(result.bytesCopied);
        const total = Number(result.sizeBytes);
        if (Number.isFinite(copied) && Number.isFinite(total) && total > 0) {
          setProgress(item, copied, total, "处理进度");
        } else {
          setProgress(item, Number.isFinite(copied) ? copied : 0, 0, "处理进度");
        }
        setState(item, "importing", labels[result.state], detail);
        await new Promise((resolve) => window.setTimeout(resolve, 2000));
      }
    } catch (error) {
      if (queue.includes(item)) {
        setState(item, "unknown", "处理状态未知", `查询失败：${error.message}。设备可能仍在处理，请重新查询。`);
      }
    } finally {
      item.tracking = false;
      if (queue.includes(item)) renderItemActions(item);
    }
  }

  async function runQueue() {
    if (busy) return;
    busy = true;
    let paused = false;
    updateControls();
    try {
      let item;
      while ((item = queue.find((entry) => entry.state === "pending"))) {
        setState(item, "uploading", "正在传输");
        setProgress(item, 0, item.size, "传输进度");
        const result = await upload(item);
        if (result.kind === "accepted") {
          item.taskId = result.taskId;
          item.file = null;
          saveTasks();
          setState(item, "importing", "已接收，正在查询处理状态");
          trackTask(item);
        } else {
          item.file = null;
          const detail = `${result.detail} 如需重试，请重新选择文件并上传。`;
          if (result.kind === "canceled") {
            setState(item, "canceled", "已请求取消传输", detail);
          } else if (result.kind === "unknown") {
            setState(item, "unknown", "接收状态未知", detail);
            paused = true;
            break;
          } else {
            setState(item, "failed", "上传失败", detail);
          }
        }
      }
    } finally {
      busy = false;
      activeRequest = null;
      activeCancelable = false;
      updateControls();
      announce(paused
        ? "队列已暂停，请确认接收状态并检查连接后继续。"
        : "队列传输处理结束，请查看各文件的状态。");
    }
  }

  function restoreTasks() {
    let records;
    try {
      records = JSON.parse(localStorage.getItem(taskStorageKey) || "[]");
    } catch {
      ui.queueHint.textContent = "浏览器无法读取已保存的任务记录，刷新后可能无法恢复查询。请保持页面开启。";
      return;
    }
    if (!Array.isArray(records)) return;
    const seen = new Set();
    for (const record of records) {
      if (!record || typeof record.taskId !== "string" || !record.taskId || seen.has(record.taskId) ||
          typeof record.name !== "string" || typeof record.path !== "string" ||
          typeof record.size !== "number" || !Number.isFinite(record.size) || record.size < 0) continue;
      seen.add(record.taskId);
      const item = makeItem(record.name, record.size, record.path, null, record.taskId);
      queue.push(item);
      ui.queueList.append(item.element);
      trackTask(item);
    }
    updateControls();
  }

  ui.chooseFiles.addEventListener("click", () => ui.fileInput.click());
  ui.fileInput.addEventListener("change", () => {
    addFiles(ui.fileInput.files);
    ui.fileInput.value = "";
  });
  ui.startUploads.addEventListener("click", runQueue);
  ui.cancelUpload.addEventListener("click", () => {
    if (activeRequest && activeCancelable) activeRequest.abort();
  });
  ui.clearPending.addEventListener("click", () => {
    for (const item of [...queue]) if (item.state === "pending") removeItem(item);
    announce("已清空待上传文件。");
  });
  ui.clearFinished.addEventListener("click", () => {
    function clearRecords() {
      for (const item of [...queue]) if (terminalStates.has(item.state)) removeItem(item);
    }
    if (queue.some((item) => item.taskId && (item.state === "failed" || item.state === "unknown"))) {
      openDialog({
        title: "清除传输记录",
        description: "只清除本网页记录，不会删除手机上的文件。清除后本网页将无法继续查询或恢复这些失败或状态未知的任务。",
        submitLabel: "清除记录",
        submit: async () => {
          clearRecords();
          return "已清除结束的传输记录。";
        },
      });
    } else {
      clearRecords();
      announce("已清除结束的传输记录。");
    }
  });
  ui.createFolder.addEventListener("click", createFolder);
  ui.refreshFiles.addEventListener("click", () => {
    if (configLoaded) loadFiles(currentPath);
    else initializeFiles();
  });

  function containsFiles(event) {
    return event.dataTransfer && Array.from(event.dataTransfer.types).includes("Files");
  }
  window.addEventListener("dragover", (event) => {
    if (containsFiles(event)) event.preventDefault();
  });
  window.addEventListener("drop", (event) => {
    if (containsFiles(event)) event.preventDefault();
  });
  ui.dropZone.addEventListener("dragover", (event) => {
    if (!containsFiles(event)) return;
    event.preventDefault();
    event.dataTransfer.dropEffect = pathReady && !filesLoading ? "copy" : "none";
    ui.dropZone.classList.add("is-dragging");
  });
  ui.dropZone.addEventListener("dragleave", (event) => {
    if (!event.relatedTarget || !ui.dropZone.contains(event.relatedTarget)) ui.dropZone.classList.remove("is-dragging");
  });
  ui.dropZone.addEventListener("drop", (event) => {
    if (!containsFiles(event)) return;
    event.preventDefault();
    ui.dropZone.classList.remove("is-dragging");
    const transfers = Array.from(event.dataTransfer.items || []);
    const files = [];
    let skippedDirectories = 0;
    if (transfers.length) {
      for (const transfer of transfers) {
        if (transfer.kind !== "file") continue;
        const entry = typeof transfer.webkitGetAsEntry === "function" ? transfer.webkitGetAsEntry() : null;
        if (entry && entry.isDirectory) {
          skippedDirectories += 1;
        } else {
          const file = transfer.getAsFile();
          if (file) files.push(file);
        }
      }
    } else {
      files.push(...event.dataTransfer.files);
    }
    addFiles(files);
    if (skippedDirectories) announce("文件夹不能直接拖放上传。请先创建文件夹，再选择其中的文件。");
  });

  renderBreadcrumbs("");
  updateControls();
  restoreTasks();
  initializeFiles();
})();
