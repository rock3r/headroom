package dev.sebastiano.headroom.auth

/**
 * The small, self-contained HTML page the loopback listener shows in the browser once sign-in
 * finishes. It links back to the app with the caller's custom scheme URL.
 */
public class CallbackPage internal constructor(public val status: Int, public val html: String) {
    public companion object {
        private const val OK = 200
        private const val BAD_REQUEST = 400

        public fun success(returnUrl: String?): CallbackPage =
            CallbackPage(
                OK,
                render(
                    title = "You're signed in",
                    message = "You can close this tab and go back to Headroom.",
                    returnUrl = returnUrl,
                ),
            )

        public fun failure(message: String, returnUrl: String?): CallbackPage =
            CallbackPage(
                BAD_REQUEST,
                render(title = "Sign-in failed", message = message, returnUrl = returnUrl),
            )

        private fun render(title: String, message: String, returnUrl: String?): String {
            val link =
                returnUrl?.let { """<a href="${escape(it)}">Return to Headroom</a>""" }.orEmpty()
            return """
                |<!doctype html>
                |<html lang="en">
                |<head>
                |<meta charset="utf-8">
                |<meta name="viewport" content="width=device-width, initial-scale=1">
                |<title>Headroom</title>
                |<style>
                |body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;
                |font-family:system-ui,sans-serif;background:#f7f7f7;color:#1c1b1f}
                |@media (prefers-color-scheme:dark){body{background:#141218;color:#e6e0e9}}
                |main{max-width:28rem;padding:2rem;text-align:center}
                |a{display:inline-block;margin-top:1.5rem;padding:.8rem 1.6rem;border-radius:999px;
                |background:#6750a4;color:#fff;text-decoration:none;font-weight:600}
                |</style>
                |</head>
                |<body><main><h1>${escape(title)}</h1><p>${escape(message)}</p>$link</main></body>
                |</html>
                |"""
                .trimMargin()
        }

        private fun escape(text: String): String =
            text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
    }
}
