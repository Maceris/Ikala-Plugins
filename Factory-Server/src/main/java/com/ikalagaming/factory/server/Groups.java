package com.ikalagaming.factory.server;

import com.ikalagaming.factory.save.Accounts;

import lombok.NonNull;

/** The permission groups that go with each account role. */
public final class Groups {

    /** Every player's group. */
    public static final String DEFAULT = "default";

    /** Operators' group, which inherits the default one. */
    public static final String OPERATOR = "operator";

    /** The owner's group, which inherits the operators' one. */
    public static final String OWNER = "owner";

    /** Not made. */
    private Groups() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * The group for a role.
     *
     * @param role The role.
     * @return Its group.
     */
    public static String of(@NonNull Accounts.Role role) {
        return switch (role) {
            case OWNER -> OWNER;
            case OPERATOR -> OPERATOR;
            case PLAYER -> DEFAULT;
        };
    }
}
