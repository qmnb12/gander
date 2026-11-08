/* Applies the selected Devanagari font to Hindi Unicode runs only.
   Document fonts remain in charge of Latin and legacy-encoded text. */
(function () {
  "use strict";
  var devanagari = /[\u0900-\u097F\u1CD0-\u1CFF\uA8E0-\uA8FF]/;
  var runs = /([\u0900-\u097F\u1CD0-\u1CFF\uA8E0-\uA8FF]+)/g;
  var queued = false;
  function scan() {
    queued = false;
    var roots = ["container", "sheet", "result", "content"].map(function (id) {
      return document.getElementById(id);
    }).filter(Boolean);
    roots.forEach(function (root) {
      var defaultFont = getComputedStyle(document.documentElement)
        .getPropertyValue("--fz-hindi-default").trim();
      root.querySelectorAll("svg text, svg tspan").forEach(function (element) {
        if (element.getAttribute("data-fz-font") || !devanagari.test(element.textContent || "")) return;
        var hasDirectHindi = Array.prototype.some.call(element.childNodes, function (child) {
          return child.nodeType === Node.TEXT_NODE && devanagari.test(child.nodeValue || "");
        });
        if (!hasDirectHindi) return;
        var current = getComputedStyle(element).fontFamily;
        var preferred = defaultFont || "'Noto Sans Devanagari'";
        var fallback = current && current !== preferred ? ", " + current : "";
        // The selected Hindi face must come first; otherwise an SVG chart's embedded
        // font always wins and the Font Center selection has no effect on chart labels.
        element.style.setProperty(
          "font-family",
          preferred + fallback + ", 'Noto Sans Devanagari', sans-serif",
          "important"
        );
        element.setAttribute("data-fz-font", "1");
      });
      var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
      var nodes = [];
      var node;
      while ((node = walker.nextNode())) {
        if (!devanagari.test(node.nodeValue || "")) continue;
        var parent = node.parentElement;
        if (!parent || parent.closest(".fz-hindi-run,script,style,textarea,input,svg")) continue;
        nodes.push(node);
      }
      nodes.forEach(function (textNode) {
        var parts = textNode.nodeValue.split(runs);
        if (parts.length < 2) return;
        var fragment = document.createDocumentFragment();
        parts.forEach(function (part) {
          if (!part) return;
          if (devanagari.test(part)) {
            var span = document.createElement("span");
            span.className = "fz-hindi-run";
            span.textContent = part;
            fragment.appendChild(span);
          } else {
            fragment.appendChild(document.createTextNode(part));
          }
        });
        if (textNode.parentNode) textNode.parentNode.replaceChild(fragment, textNode);
      });
    });
  }
  function schedule() {
    if (queued) return;
    queued = true;
    window.requestAnimationFrame(scan);
  }
  if (document.body) {
    new MutationObserver(schedule).observe(document.body, {
      childList: true, characterData: true, subtree: true
    });
  }
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", schedule, { once: true });
  } else {
    schedule();
  }
  window.addEventListener("load", schedule, { once: true });
})();