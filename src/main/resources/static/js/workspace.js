/* Editor + preview page: Monaco, save, copy, format, live preview via postMessage. */
(function () {
  "use strict";

  var data = window.__GENERATION__;
  var frame = document.getElementById("preview-frame");
  var toastElement = document.getElementById("toast");
  var saveState = document.getElementById("save-state");
  var previewStatus = document.getElementById("preview-status");
  var savedCode = data.code;
  var editor, componentModel, notesModel;
  var pushTimer = null;

  function csrfHeaders() {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    var headers = { "Content-Type": "application/json" };
    if (token && header) headers[header.content] = token.content;
    return headers;
  }

  function toast(message, kind) {
    toastElement.textContent = message;
    toastElement.className = "toast show " + (kind || "");
    clearTimeout(toast.timer);
    toast.timer = setTimeout(function () { toastElement.className = "toast"; }, 2400);
  }

  function setPreviewStatus(state, text) {
    previewStatus.innerHTML = "";
    var dot = document.createElement("span");
    dot.className = "dot " + (state === "ok" ? "dot-ok" : state === "error" ? "dot-error" : "dot-pending");
    previewStatus.appendChild(dot);
    previewStatus.appendChild(document.createTextNode(" " + text));
    previewStatus.title = state === "error" ? text : "";
  }

  function markDirty() {
    var dirty = componentModel.getValue() !== savedCode;
    saveState.textContent = dirty ? "Unsaved changes" : "Saved";
    saveState.classList.toggle("badge-warn", dirty);
  }

  function pushToPreview() {
    if (!frame.contentWindow) return;
    setPreviewStatus("pending", "Rendering");
    frame.contentWindow.postMessage({ source: "figma-editor", type: "code", code: componentModel.getValue() }, "*");
  }

  function schedulePush() {
    clearTimeout(pushTimer);
    pushTimer = setTimeout(pushToPreview, 500);
  }

  function save() {
    var code = componentModel.getValue();
    return fetch("/generations/" + encodeURIComponent(data.id) + "/code", {
      method: "POST",
      headers: csrfHeaders(),
      body: JSON.stringify({ code: code }),
      credentials: "same-origin",
    }).then(function (response) {
      if (!response.ok) throw new Error("HTTP " + response.status);
      savedCode = code;
      markDirty();
      toast("Saved", "ok");
    }).catch(function (error) {
      toast("Save failed: " + error.message, "error");
    });
  }

  window.addEventListener("message", function (event) {
    if (event.source !== frame.contentWindow) return;
    var message = event.data || {};
    if (message.source !== "figma-preview" || message.type !== "status") return;
    if (message.ok) setPreviewStatus("ok", "Rendered");
    else setPreviewStatus("error", message.error || "Error");
  });

  // Preview / Figma original tabs
  document.querySelectorAll("[data-view]").forEach(function (tab) {
    tab.addEventListener("click", function () {
      document.querySelectorAll("[data-view]").forEach(function (t) { t.classList.toggle("active", t === tab); });
      var showFigma = tab.getAttribute("data-view") === "figma";
      document.getElementById("preview-wrap").hidden = showFigma;
      var original = document.getElementById("figma-original");
      if (original) original.hidden = !showFigma;
    });
  });

  // Viewport widths
  document.querySelectorAll("[data-width]").forEach(function (button) {
    button.addEventListener("click", function () {
      document.querySelectorAll("[data-width]").forEach(function (b) { b.classList.toggle("active", b === button); });
      var width = button.getAttribute("data-width");
      var wrap = document.getElementById("preview-wrap");
      wrap.style.width = width === "fill" ? "100%" : width + "px";
      wrap.classList.toggle("framed", width !== "fill");
    });
  });

  document.getElementById("btn-reload").addEventListener("click", function () {
    setPreviewStatus("pending", "Rendering");
    frame.addEventListener("load", function once() {
      frame.removeEventListener("load", once);
      pushToPreview();
    });
    frame.src = frame.src;
  });

  // Absolute URLs: Monaco starts its workers from a blob, where relative paths do not resolve
  var vsBase = window.location.origin + "/webjars/monaco-editor/min/vs";
  window.MonacoEnvironment = {
    getWorkerUrl: function () {
      return "data:text/javascript;charset=utf-8," + encodeURIComponent(
        "self.MonacoEnvironment = { baseUrl: \"" + window.location.origin + "/webjars/monaco-editor/min/\" };" +
        "importScripts(\"" + vsBase + "/base/worker/workerMain.js\");");
    },
  };
  require.config({ paths: { vs: vsBase } });
  require(["vs/editor/editor.main"], function () {
    monaco.languages.typescript.typescriptDefaults.setDiagnosticsOptions({
      noSemanticValidation: true,
      noSyntaxValidation: false,
    });
    monaco.languages.typescript.typescriptDefaults.setCompilerOptions({
      jsx: monaco.languages.typescript.JsxEmit.React,
      target: monaco.languages.typescript.ScriptTarget.ES2020,
      allowNonTsExtensions: true,
    });

    componentModel = monaco.editor.createModel(data.code, "typescript",
      monaco.Uri.parse("file:///src/" + data.componentName + ".tsx"));
    notesModel = monaco.editor.createModel(data.notes || "", "markdown", monaco.Uri.parse("file:///FIGMA_NOTES.md"));

    editor = monaco.editor.create(document.getElementById("editor"), {
      model: componentModel,
      theme: "vs-dark",
      automaticLayout: true,
      fontSize: 13,
      fontFamily: "'JetBrains Mono', 'Fira Code', ui-monospace, SFMono-Regular, Menlo, Consolas, monospace",
      minimap: { enabled: true },
      scrollBeyondLastLine: false,
      wordWrap: "on",
      tabSize: 2,
      renderLineHighlight: "all",
      smoothScrolling: true,
      padding: { top: 12 },
    });

    componentModel.onDidChangeContent(function () {
      markDirty();
      schedulePush();
    });
    editor.onDidChangeCursorPosition(function (e) {
      document.getElementById("cursor-pos").textContent = "Ln " + e.position.lineNumber + ", Col " + e.position.column;
    });
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, save);

    document.querySelectorAll("[data-file]").forEach(function (tab) {
      tab.addEventListener("click", function () {
        document.querySelectorAll("[data-file]").forEach(function (t) { t.classList.toggle("active", t === tab); });
        var notes = tab.getAttribute("data-file") === "notes";
        editor.setModel(notes ? notesModel : componentModel);
        editor.updateOptions({ readOnly: notes });
      });
    });

    document.getElementById("btn-save").addEventListener("click", save);
    document.getElementById("btn-format").addEventListener("click", function () {
      editor.setModel(componentModel);
      editor.getAction("editor.action.formatDocument").run();
    });
    document.getElementById("btn-copy").addEventListener("click", function () {
      var text = editor.getModel().getValue();
      (navigator.clipboard ? navigator.clipboard.writeText(text) : Promise.reject(new Error("unavailable")))
        .then(function () { toast("Copied to clipboard", "ok"); })
        .catch(function () {
          editor.focus();
          editor.setSelection(editor.getModel().getFullModelRange());
          toast("Press Ctrl/Cmd+C to copy", "");
        });
    });
    document.getElementById("btn-download").addEventListener("click", function (event) {
      if (componentModel.getValue() !== savedCode) {
        event.preventDefault();
        var href = this.href;
        save().then(function () { window.location.href = href; });
      }
    });
    window.addEventListener("beforeunload", function (event) {
      if (componentModel.getValue() !== savedCode) {
        event.preventDefault();
        event.returnValue = "";
      }
    });
  });
})();
