package dev.sebastiano.headroom.auth

/** Every failure in this module is one of these, so callers can show the right message. */
public sealed class AuthException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** The provider could not be reached. Stored tokens may still be valid: do not sign out. */
    public class Network(message: String, cause: Throwable? = null) : AuthException(message, cause)

    /**
     * The provider answered with an error status. When [requiresSignIn] is true the stored tokens
     * were rejected (for example a revoked or already used refresh token) and the user must sign in
     * again.
     */
    public class Rejected(
        public val status: Int,
        public val error: String?,
        message: String,
    ) : AuthException(message) {
        public val requiresSignIn: Boolean
            get() = status in SIGN_IN_STATUSES

        private companion object {
            val SIGN_IN_STATUSES = setOf(400, 401, 403)
        }
    }

    /** The provider answered with something this module cannot read. */
    public class InvalidResponse(message: String, cause: Throwable? = null) :
        AuthException(message, cause)

    /** The user canceled or denied sign-in, or the callback did not match this sign-in. */
    public class SignInFailed(message: String) : AuthException(message)

    /** Sign-in was not finished in time, for example an expired device code. */
    public class TimedOut(message: String) : AuthException(message)

    /** The saved sign-in expired and cannot be refreshed. The user must sign in again. */
    public class SignInExpired(public val accountId: String) :
        AuthException("The sign-in for account $accountId expired")

    /** No credential is saved for this account. */
    public class NotSignedIn(public val accountId: String) :
        AuthException("No saved sign-in for account $accountId")
}
