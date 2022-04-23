package com.ikalagaming.permissions;

import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A group that can be assigned permissions. Entities that are members of these
 * groups are assigned the permissions of the group, as well as any permissions
 * specific to that entity which may have been assigned. <br>
 * Groups may have a parent group which they inherit permissions from.
 * Permissions set in a group override permissions set in a parent group if
 * there are any conflicts.
 *
 * @author Ches Burks
 *
 */
@Slf4j
public class PermissionGroup implements PermissionHolder {

	private static final PermissionGroup ROOT;

	static {
		PermissionGroup rootGroup = null;
		try {
			rootGroup = new PermissionGroup("root", null);
		}
		catch (DuplicateGroupException e) {
			PermissionGroup.log.warn(SafeResourceLoader.getString(
				"DUPLICATE_PERMISSION", Permission.RESOURCE_LOCATION), "root");
		}
		/*
		 * May be null at this point, but logs will help determine why things
		 * break later
		 */
		ROOT = rootGroup;
	}

	private static HashMap<String, PermissionGroup> groupsByName =
		new HashMap<>();

	/**
	 * If the group {@link #groupExists(String) exists}, returns the group. If
	 * the group has not been created, returns null.
	 *
	 * @param name the name of the group to return
	 * @return the group, or null if no group with that name exists
	 */
	public static Optional<PermissionGroup>
		getGroupByName(@NonNull String name) {
		return Optional.ofNullable(PermissionGroup.groupsByName.get(name));

	}

	/**
	 * Returns true if a group with the given name has been created.
	 *
	 * @param name the name of the group
	 * @return true if the group exists
	 */
	public static boolean groupExists(String name) {
		return PermissionGroup.groupsByName.containsKey(name);
	}

	/**
	 * The name of this group.
	 *
	 * @return The groups name.
	 */
	@SuppressWarnings("javadoc")
	@Getter
	private final String groupName;

	/**
	 * The parent group, if it exists. May be null.
	 *
	 * @return This groups parent, may be null.
	 */
	@SuppressWarnings("javadoc")
	@Getter
	private final PermissionGroup parent;

	/**
	 * Permissions that are set for this group. Inherits permissions from the
	 * parent, but children override parents for permission values if they both
	 * have a permission. The boolean for each permission is true if that
	 * permission is granted to the group, and false if the permission is
	 * revoked from the group.
	 */
	private Map<Permission, Boolean> permissions;

	/**
	 * The description of the group.
	 *
	 * @return a brief description of this group.
	 */
	@SuppressWarnings("javadoc")
	@Getter
	private final String description;

	/**
	 * Constructs a new {@link PermissionGroup} with the supplied information.
	 * The parent defaults to "root", and the description to an empty string.
	 * <p>
	 * The name is a unique name for the group, which matches the name in
	 * configuration files. Errors are thrown if the name is null or empty, or
	 * if a group with the supplied name already exists.<br>
	 * Example: "heroes" or "moderator"
	 * </p>
	 * <p>
	 * The permissions are a list of permissions and a boolean indicating if the
	 * group is allowed or denied that permission. If the permission is mapped
	 * to true, the group has that permission, if it is set to false, it does
	 * not. Permissions that are not set use their default value.<br>
	 * Example: "entity.jump" is mapped to "true"
	 * </p>
	 *
	 * @param name The name of the group
	 * @param newPermissions Permissions this group is assigned
	 * @throws DuplicateGroupException If a group with that name already exists
	 * @throws EmptyGroupNameException If the group name is empty or null
	 */
	public PermissionGroup(String name, Map<Permission, Boolean> newPermissions)
		throws DuplicateGroupException {
		this(name, null, null, newPermissions);
	}

	/**
	 * Constructs a new {@link PermissionGroup} with the supplied information.
	 * The description defaults to an empty string.
	 * <p>
	 * The name is a unique name for the group, which matches the name in
	 * configuration files. Errors are thrown if the name is null or empty, or
	 * if a group with the supplied name already exists.<br>
	 * Example: "heroes" or "moderator"
	 * </p>
	 * <p>
	 * The parent group is the group this group inherits permissions from.
	 * Permissions set in this group override all parent permissions. If no
	 * parent is set, it will default to the "root" group.<br>
	 * Example: "npcs" or "heroes"
	 * </p>
	 * <p>
	 * The permissions are a list of permissions and a boolean indicating if the
	 * group is allowed or denied that permission. If the permission is mapped
	 * to true, the group has that permission, if it is set to false, it does
	 * not. Permissions that are not set use their default value.<br>
	 * Example: "entity.jump" is mapped to "true"
	 * </p>
	 *
	 * @param name The name of the group
	 * @param theParent The parent of this group
	 * @param newPermissions Permissions this group is assigned
	 * @throws DuplicateGroupException If a group with that name already exists
	 * @throws EmptyGroupNameException If the group name is empty or null
	 */
	public PermissionGroup(String name, PermissionGroup theParent,
		Map<Permission, Boolean> newPermissions)
		throws DuplicateGroupException {
		this(name, null, theParent, newPermissions);
	}

	/**
	 * Constructs a new {@link PermissionGroup} with the supplied information.
	 * The parent defaults to "root".
	 * <p>
	 * The name is a unique name for the group, which matches the name in
	 * configuration files. Errors are thrown if the name is null or empty, or
	 * if a group with the supplied name already exists.<br>
	 * Example: "heroes" or "moderator"
	 * </p>
	 * <p>
	 * The description is a brief explanation of the groups purpose. Who is in
	 * the group or what it adds/removes from members may be a description. If
	 * no description is provided, it defaults to an empty string. <br>
	 * Examples: "Players that may use all transportation systems" or "Entities
	 * that should not die"
	 * </p>
	 * <p>
	 * The permissions are a list of permissions and a boolean indicating if the
	 * group is allowed or denied that permission. If the permission is mapped
	 * to true, the group has that permission, if it is set to false, it does
	 * not. Permissions that are not set use their default value.<br>
	 * Example: "entity.jump" is mapped to "true"
	 * </p>
	 *
	 * @param name The name of the group
	 * @param theDescription The description of the group
	 * @param newPermissions Permissions this group is assigned
	 * @throws DuplicateGroupException If a group with that name already exists
	 * @throws EmptyGroupNameException If the group name is empty or null
	 */
	public PermissionGroup(String name, String theDescription,
		Map<Permission, Boolean> newPermissions)
		throws DuplicateGroupException {
		this(name, theDescription, null, newPermissions);
	}

	/**
	 * Constructs a new {@link PermissionGroup} with the supplied information.
	 * <p>
	 * The name is a unique name for the group, which matches the name in
	 * configuration files. Errors are thrown if the name is null or empty, or
	 * if a group with the supplied name already exists.<br>
	 * Example: "heroes" or "moderator"
	 * </p>
	 * <p>
	 * The description is a brief explanation of the groups purpose. Who is in
	 * the group or what it adds/removes from members may be a description. If
	 * no description is provided, it defaults to an empty string. <br>
	 * Examples: "Players that may use all transportation systems" or "Entities
	 * that should not die"
	 * </p>
	 * <p>
	 * The parent group is the group this group inherits permissions from.
	 * Permissions set in this group override all parent permissions. If no
	 * parent is set, it will default to the "root" group.<br>
	 * Example: "npcs" or "heroes"
	 * </p>
	 * <p>
	 * The permissions are a list of permissions and a boolean indicating if the
	 * group is allowed or denied that permission. If the permission is mapped
	 * to true, the group has that permission, if it is set to false, it does
	 * not. Permissions that are not set use their default value.<br>
	 * Example: "entity.jump" is mapped to "true"
	 * </p>
	 *
	 * @param name The name of the group
	 * @param theDescription The description of the group
	 * @param theParent The parent of this group
	 * @param newPermissions Permissions this group is assigned
	 * @throws DuplicateGroupException If a group with that name already exists
	 * @throws EmptyGroupNameException If the group name is empty or null
	 */
	public PermissionGroup(String name, String theDescription,
		PermissionGroup theParent, Map<Permission, Boolean> newPermissions) {
		if (name == null || name.isEmpty()) {
			throw new EmptyGroupNameException();
		}
		if (PermissionGroup.groupExists(name)) {
			throw new DuplicateGroupException();
		}
		this.groupName = name;

		if (theDescription == null) {
			this.description = "";
		}
		else {
			this.description = theDescription.isEmpty() ? "" : theDescription;
		}
		if (theParent != null) {
			this.parent = theParent;
		}
		else {
			if (!this.equals(PermissionGroup.ROOT)) {
				this.parent = PermissionGroup.ROOT;
			}
			else {
				// the root node
				this.parent = null;
			}
		}
		this.permissions =
			newPermissions == null ? new HashMap<>() : newPermissions;

		// TODO calculate permissions
		PermissionGroup.groupsByName.put(name, this);

	}

	private int getDepth(Permission container, Permission child, int oldDepth) {
		if (child.getName().equals(container.getName())) {
			return oldDepth;
		}
		if (container.contains(child)) {
			for (String s : container.getChildPermissions().keySet()) {
				Optional<Permission> maybePerm = Permission.getByName(s);
				if (!maybePerm.isPresent()) {
					continue;
				}
				if (maybePerm.get().contains(child)) {
					return this.getDepth(maybePerm.get(), child, oldDepth + 1);
				}
			}

		}
		// It did not contain the value, but should have.
		// make sure it is not going to be the least depth
		return Integer.MAX_VALUE - 1;
	}

	/**
	 * Returns true if this has a parent. If this groups parent is null, returns
	 * false.
	 *
	 * @return true if this group has a parent group
	 */
	public boolean hasParent() {
		return this.parent != null;
	}

	@Override
	public boolean hasPermission(Permission perm) {
		ArrayList<Permission> containers = new ArrayList<>();
		boolean duplicateEntryFlag = false;
		for (Map.Entry<Permission, Boolean> entry : this.permissions
			.entrySet()) {
			if (entry.getKey().getName().equals(perm.getName())) {
				return entry.getValue();
			}
			else if (entry.getKey().contains(perm)) {
				containers.add(entry.getKey());
				duplicateEntryFlag = true;
			}
		}
		// there was more than one subtree that has the permission
		if (duplicateEntryFlag) {
			Permission lowest = null;
			int lowestVal = Integer.MAX_VALUE;
			int currentVal = 0;
			for (Permission p : containers) {
				currentVal = this.getDepth(p, perm, 0);
				if (currentVal <= lowestVal) {
					/*
					 * If two values have the same depth, this will end up being
					 * the last value added that has that level.
					 */
					lowestVal = currentVal;
					lowest = p;
				}
			}
			if (lowest != null && this.permissions.containsKey(lowest)) {
				return this.permissions.get(lowest);
			}
		}

		return perm.getDefault();
	}

	@Override
	public boolean isPermissionSet(Permission perm) {
		for (Permission permission : this.permissions.keySet()) {
			if (permission.contains(perm)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void recalculatePermissions() {
		// TODO implement this
	}

}
