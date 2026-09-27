package dev.sebastiano.headroom.model

/** Which windows may send a "your limit has reset" notification. */
public object ResetPolicy {
    /** Session and daily windows reset too often to be worth an alert. */
    public fun canAlert(window: QuotaWindow): Boolean =
        window.kind == WindowKind.Weekly || window.kind == WindowKind.Monthly

    /** Weekly windows alert unless the user turns them off. Monthly ones are opt-in. */
    public fun alertsByDefault(window: QuotaWindow): Boolean = window.kind == WindowKind.Weekly
}
