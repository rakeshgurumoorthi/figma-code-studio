/*
 * Runs inside the sandboxed preview iframe. Compiles the generated React + Tailwind TSX
 * with Babel, renders it, and re-renders whenever the editor posts new code.
 */
(function () {
  "use strict";

  var config = window.__PREVIEW__ || { code: "", assets: {} };
  var rootElement = document.getElementById("root");
  var errorElement = document.getElementById("preview-error");
  var root = ReactDOM.createRoot(rootElement);

  var modules = {
    react: React,
    "react-dom": ReactDOM,
    "react-dom/client": ReactDOM,
    "react/jsx-runtime": jsxRuntime(),
  };

  // React's automatic JSX runtime is not in the UMD build; map it onto createElement.
  function jsxRuntime() {
    function jsx(type, props, key) {
      var p = Object.assign({}, props);
      if (key !== undefined) p.key = key;
      return React.createElement(type, p);
    }
    return { jsx: jsx, jsxs: jsx, Fragment: React.Fragment };
  }

  function report(ok, message) {
    try {
      window.parent.postMessage({ source: "figma-preview", type: "status", ok: ok, error: message || null }, "*");
    } catch (e) { /* parent gone */ }
  }

  function showError(title, error) {
    var text = error && error.message ? error.message : String(error);
    errorElement.innerHTML = "";
    var heading = document.createElement("strong");
    heading.textContent = title;
    errorElement.appendChild(heading);
    errorElement.appendChild(document.createTextNode(text));
    errorElement.style.display = "block";
    report(false, title + ": " + text);
  }

  function clearError() {
    errorElement.style.display = "none";
    errorElement.textContent = "";
  }

  // Generated code refers to images as "/assets/name"; swap in the data: URIs we were given.
  function inlineAssets(code) {
    var assets = config.assets || {};
    Object.keys(assets).forEach(function (path) {
      code = code.split('"' + path + '"').join('"' + assets[path] + '"');
      code = code.split("'" + path + "'").join("'" + assets[path] + "'");
      code = code.split("(" + path + ")").join("(" + assets[path] + ")");
    });
    return code;
  }

  function compile(source) {
    return Babel.transform(source, {
      filename: "Component.tsx",
      sourceType: "module",
      presets: [
        ["env", { modules: "commonjs", targets: { chrome: "100" } }],
        ["react", { runtime: "classic" }],
        ["typescript", { isTSX: true, allExtensions: true }],
      ],
    }).code;
  }

  function evaluate(compiled) {
    var module = { exports: {} };
    function require(name) {
      if (Object.prototype.hasOwnProperty.call(modules, name)) return modules[name];
      if (/\.css$/.test(name)) return {};
      throw new Error('Import "' + name + '" is not available in the preview. Only React is provided.');
    }
    var factory = new Function("require", "module", "exports", "React", compiled);
    factory(require, module, module.exports, React);
    var exported = module.exports;
    if (exported && exported.__esModule && typeof exported.default === "function") return exported.default;
    if (typeof exported.default === "function") return exported.default;
    var names = Object.keys(exported);
    for (var i = 0; i < names.length; i++) {
      if (typeof exported[names[i]] === "function") return exported[names[i]];
    }
    // No export at all: look for a capitalised top-level function in the source
    var match = /function\s+([A-Z][\w$]*)\s*\(/.exec(compiled);
    if (match) {
      return new Function("require", "module", "exports", "React", compiled + "\nreturn " + match[1] + ";")(
        require, { exports: {} }, {}, React);
    }
    throw new Error("No React component was found. Export one with `export default function Name() { ... }`.");
  }

  class ErrorBoundary extends React.Component {
    constructor(props) {
      super(props);
      this.state = { error: null };
    }
    static getDerivedStateFromError(error) {
      return { error: error };
    }
    componentDidCatch(error) {
      showError("Runtime error", error);
    }
    render() {
      return this.state.error ? null : this.props.children;
    }
  }

  var renderCount = 0;
  function render(source) {
    var Component;
    try {
      Component = evaluate(compile(inlineAssets(source)));
    } catch (error) {
      showError("Compile error", error);
      return;
    }
    clearError();
    renderCount += 1;
    // A new key resets the error boundary on every edit
    root.render(React.createElement(ErrorBoundary, { key: renderCount }, React.createElement(Component)));
    setTimeout(function () {
      if (errorElement.style.display !== "block") report(true);
    }, 0);
  }

  window.addEventListener("message", function (event) {
    if (event.source !== window.parent) return;
    var data = event.data || {};
    if (data.source === "figma-editor" && data.type === "code" && typeof data.code === "string") {
      render(data.code);
    }
  });

  window.addEventListener("error", function (event) {
    showError("Runtime error", event.error || event.message);
  });

  render(config.code || "");
})();
