package dev.expedition.bridge.portal;

/**
 * Guards the destination of managed rifts. Bridge-owned exits and entrances, and any rift on a
 * protected position, may only be rewritten by the bridge itself (to another bridge-owned
 * target). DD's rift configuration tool and similar items rewrite RiftBlockEntity destinations
 * without touching blocks, so the block-write guard alone cannot cover them.
 */
public final class ManagedRiftGuard {
    private ManagedRiftGuard() {}

    /** Pure decision: allow exactly when the next target is bridge-owned or nothing is protected. */
    public static boolean allowsRewrite(boolean currentManaged, boolean protectedPosition, boolean nextManaged) {
        return nextManaged || (!currentManaged && !protectedPosition);
    }
}
