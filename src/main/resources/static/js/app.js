/*
 * Small progressive-enhancement layer on top of vendored htmx:
 * clipboard-copy for rendered PNRs (spec: "let the user copy each PNR to
 * the clipboard without manual text selection"). Everything else
 * (add/remove passenger row, hx-post submit, blocking spinner) is plain
 * HTML/htmx attributes declared directly in the templates — no JS needed
 * for those.
 *
 * Delegated on `document.body` (not per-button) so it keeps working after
 * htmx swaps new PNR cards into the DOM.
 */
document.addEventListener("click", function (event) {
  var button = event.target.closest("[data-copy-pnr]");
  if (!button) {
    return;
  }
  var pnr = button.getAttribute("data-copy-pnr");
  if (!pnr || !navigator.clipboard) {
    return;
  }
  navigator.clipboard.writeText(pnr).then(function () {
    var original = button.textContent;
    button.textContent = "Copied";
    setTimeout(function () {
      button.textContent = original;
    }, 1500);
  });
});
