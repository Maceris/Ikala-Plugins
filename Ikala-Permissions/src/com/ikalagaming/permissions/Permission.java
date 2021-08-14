package com.ikalagaming.permissions;

import com.ikalagaming.plugins.PluginManager;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.CustomLog;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a unique permission.
 *
 * @author Ches Burks
 *
 */
@CustomLog(topic = PluginManager.PLUGIN_NAME)
public class Permission {

	/**
	 * The default value for a permission.
	 */
	private static final boolean DEFAULT_PERMISSION = false;

	/**
	 * The location for permissions resource bundle
	 */
	static final String RESOURCE_LOCATION =
		"com.ikalagaming.permissions.resources.Permissions";

	private static HashMap<String, Permission> permissionByName =
		new HashMap<>();

	/**
	 * Checks to see if a permission with the given name has already been
	 * created. If it exists, this returns true.
	 *
	 * @param permissionName the fully qualified permission name
	 * @return true if the permission exists
	 */
	public static boolean exists(String permissionName) {
		return Permission.permissionByName.containsKey(permissionName);
	}

	private static Map<String, Boolean> extractChildren(Map<?, ?> input,
		String name, boolean defaultPermission, List<Permission> output) {
		Map<String, Boolean> children = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : input.entrySet()) {
			if ((entry.getValue() instanceof Boolean)) {
				children.put(entry.getKey().toString(),
					(Boolean) entry.getValue());
			}
			else if ((entry.getValue() instanceof Map)) {
				try {
					Permission perm = Permission.loadPermission(
						entry.getKey().toString(), (Map<?, ?>) entry.getValue(),
						defaultPermission, output);
					children.put(name, true);
					if (output != null) {
						output.add(perm);
					}
				}
				catch (Exception ex) {
					throw new IllegalArgumentException(
						"Permission node '" + entry.getKey().toString()
							+ "' in child of " + name + " is invalid",
						ex);
				}
			}
			else {
				throw new IllegalArgumentException("Child '"
					+ entry.getKey().toString() + "' contains invalid value");
			}
		}
		return children;
	}

	/**
	 * If the permission named {@link #exists(String) already exists}, returns
	 * that permission. Returns null if the permission does not already exist.
	 *
	 * @param permissionName the fully qualified permission name
	 * @return the permission with the given name, if it exists
	 */
	public static Optional<Permission> getByName(String permissionName) {
		return Optional
			.ofNullable(Permission.permissionByName.get(permissionName));
	}

	private static boolean isValidName(String name) {
		if (name.endsWith(".")) {
			return false;
		}
		if (name.startsWith(".")) {
			return false;
		}
		for (char c : name.toCharArray()) {
			if (!(Character.isLetterOrDigit(c) || c == '.')) {
				// if the character is not a number, letter or period
				return false;
			}
		}
		return true;
	}

	/**
	 * Loads a Permission from a map, usually used from retrieval from a yaml
	 * file.
	 * <p>
	 * The data may contain the following keys:
	 * <ul>
	 * <li>default: Boolean true or false. If not specified, false.
	 * <li>children: Map(String, Boolean) of child permissions. If not
	 * specified, empty list.
	 * <li>description: Short string containing a very small description of this
	 * permission. If not specified, empty string.
	 * </ul>
	 *
	 * @param name Name of the permission
	 * @param data Map of keys to load from
	 * @param def Default permission value to use if not set
	 * @param output A list to append any created child-Permissions to. This may
	 *            be null
	 * @return Permission the permissions object
	 * @throws NullPointerException if the name or data provided is null
	 * @throws IllegalArgumentException if the permissions had invalid data
	 */
	public static Permission loadPermission(String name, Map<?, ?> data,
		boolean def, List<Permission> output)
		throws NullPointerException, IllegalArgumentException {
		boolean defau = def;
		if (name == null) {
			throw new NullPointerException("Name cannot be null");
		}
		if (data == null) {
			throw new NullPointerException("Data cannot be null");
		}
		String desc = null;
		Map<String, Boolean> children = null;
		if (data.get("default") != null) {
			Object theDefault = data.get("default");
			if (!DefaultPermissionValue.isValid(theDefault.toString())) {
				throw new IllegalArgumentException(
					"'default' key contained unknown value");
			}

			defau =
				DefaultPermissionValue.getByName(theDefault.toString()).value();

		}
		if (data.get("children") != null) {
			Object childrenNode = data.get("children");
			if (childrenNode instanceof Iterable) {
				children = new LinkedHashMap<>();
				for (Object child : (Iterable<?>) childrenNode) {
					if (child != null) {
						children.put(child.toString(), true);
					}
				}
			}
			else if (childrenNode instanceof Map) {
				children = Permission.extractChildren((Map<?, ?>) childrenNode,
					name, defau, output);
			}
			else {
				throw new IllegalArgumentException(
					"'children' key is of wrong type");
			}
		}
		if (data.get("description") != null) {
			desc = data.get("description").toString();
		}

		return new Permission(name, desc, defau, children);
	}

	/**
	 * Loads a Permission from a map, usually used from retrieval from a yaml
	 * file.
	 * <p>
	 * The data may contain the following keys:
	 * <ul>
	 * <li>default: Boolean true or false. If not specified, false.
	 * <li>children: Map(String, Boolean) of child permissions. If not
	 * specified, empty list.
	 * <li>description: Short string containing a very small description of this
	 * description. If not specified, empty string.
	 * </ul>
	 *
	 * @param name Name of the permission
	 * @param data Map of keys
	 * @return Permission the permissions object
	 */
	public static Permission loadPermission(String name,
		Map<String, Object> data) {
		return Permission.loadPermission(name, data,
			Permission.DEFAULT_PERMISSION, null);
	}

	/**
	 * Loads a list of Permissions from a map, usually used from retrieval from
	 * a yaml file.
	 * <p>
	 * The data may contain a list of name:data, where the data contains the
	 * following keys:
	 * <ul>
	 * <li>default: Boolean true or false. If not specified, false.
	 * <li>children: Map(String, Boolean) of child permissions. If not
	 * specified, empty list.
	 * <li>description: Short string containing a very small description of this
	 * description. If not specified, empty string.
	 * </ul>
	 *
	 * @param data Map of permissions
	 * @param defaultPerm Default permission value to use if missing
	 * @return Permission object
	 */
	public static List<Permission> loadPermissions(Map<?, ?> data,
		boolean defaultPerm) {

		List<Permission> result = new ArrayList<>();

		for (Map.Entry<?, ?> entry : data.entrySet()) {
			try {
				result.add(Permission.loadPermission(entry.getKey().toString(),
					(Map<?, ?>) entry.getValue(), defaultPerm, result));
			}
			catch (Exception ex) {
				String msg = SafeResourceLoader.getString("INVALID_PERMISSIONS",
					Permission.RESOURCE_LOCATION);
				Permission.log.warning(msg);

				ex.printStackTrace();
			}
		}
		return result;
	}

	/**
	 * The default for this permission. <br>
	 * Example: "false"
	 */
	private boolean defaultValue;

	/**
	 * The name that identifies the permission, which is the same string as
	 * would be found in permission yaml files. <br>
	 * Example: "entity.movement"
	 *
	 * @return The fully qualified name for this permission.
	 */
	@SuppressWarnings("javadoc")
	@Getter
	private final String name;

	/**
	 * A short description of the purpose for the permission, if it is set. An
	 * empty string if it is not set. <br>
	 * Example: "This allows entities to move around the world"
	 *
	 * @return Brief description of this permission.
	 */
	@SuppressWarnings("javadoc")
	@Getter
	private final String description;

	/**
	 * A mapping of child permissions that this permissions includes. Each child
	 * permission has a boolean assigned to it. If the boolean is true, the
	 * child permission will inherit this permissions value. If it is false, it
	 * will inherit the inverse of this permissions value.<br>
	 * Example: "entity.jump" is mapped to "true"
	 *
	 * @return The list of child permissions.
	 */
	@SuppressWarnings("javadoc")
	@Getter
	private final Map<String, Boolean> childPermissions;

	/**
	 * A map of the full list of subchildren, calculated recursively.
	 */
	private HashMap<String, Boolean> fullChildMap;

	/**
	 * Used so that the subchild map is only calculated once to save memory.
	 */
	private boolean childrenCalculated = false;

	/**
	 * <p>
	 * Constructs a new permission with a name. The description defaults to an
	 * empty string. The default value defaults to the default for permissions.
	 * The children list defaults to empty.<br>
	 * </p>
	 * <p>
	 * The name is a string that identifies the permission, and is the same
	 * string as would be found in permission yaml files. <br>
	 * Example: "entity.movement"
	 * </p>
	 *
	 * @param name The name of the permission
	 * @throws IllegalArgumentException If the name is invalid or already exists
	 *
	 * @see #Permission(String, String, boolean, Map)
	 */
	public Permission(String name) {
		this(name, null, Permission.DEFAULT_PERMISSION, null);
	}

	/**
	 * <p>
	 * Constructs a new permission with a name and default value. The
	 * description defaults to an empty string. The children list defaults to
	 * empty.<br>
	 * </p>
	 * <p>
	 * The name is a string that identifies the permission, and is the same
	 * string as would be found in permission yaml files. <br>
	 * Example: "entity.movement"
	 * </p>
	 * <p>
	 * The default value is a value that determines the default for this
	 * permission. <br>
	 * Example: "false"
	 * </p>
	 *
	 * @param name The name of the permission
	 * @param defaultValue the default value for the permission
	 * @throws IllegalArgumentException If the name is invalid or already exists
	 *
	 * @see #Permission(String, String, boolean, Map)
	 */
	public Permission(String name, boolean defaultValue) {
		this(name, null, defaultValue, null);
	}

	/**
	 * <p>
	 * Constructs a new permission with a name, default value, and children
	 * list. The description defaults to an empty string.<br>
	 * </p>
	 * <p>
	 * The name is a string that identifies the permission, and is the same
	 * string as would be found in permission yaml files. <br>
	 * Example: "entity.movement"
	 * </p>
	 * <p>
	 * The default value is a value that determines the default for this
	 * permission. <br>
	 * Example: "false"
	 * </p>
	 * <p>
	 * The children is a list of permissions that are granted or revoked when
	 * this permission is granted/revoked. Each child permission has a boolean
	 * assigned to it. If the boolean is true, the child permission will inherit
	 * this permissions value. If it is false, it will inherit the inverse of
	 * this permissions value. <br>
	 * Example: "entity.jump" is mapped to "true"
	 * </p>
	 *
	 * @param name The name of the permission
	 * @param defaultValue the default value for the permission
	 * @param children children this permission includes
	 * @throws IllegalArgumentException If the name is invalid or already exists
	 *
	 * @see #Permission(String, String, boolean, Map)
	 */
	public Permission(String name, boolean defaultValue,
		Map<String, Boolean> children) {
		this(name, null, defaultValue, children);
	}

	/**
	 * <p>
	 * Constructs a new permission with a name, and children list. The default
	 * value defaults to the default for permissions. The description defaults
	 * to an empty string.<br>
	 * </p>
	 * <p>
	 * The name is a string that identifies the permission, and is the same
	 * string as would be found in permission yaml files. <br>
	 * Example: "entity.movement"
	 * </p>
	 * <p>
	 * The children is a list of permissions that are granted or revoked when
	 * this permission is granted/revoked. Each child permission has a boolean
	 * assigned to it. If the boolean is true, the child permission will inherit
	 * this permissions value. If it is false, it will inherit the inverse of
	 * this permissions value. <br>
	 * Example: "entity.jump" is mapped to "true"
	 * </p>
	 *
	 * @param name The name of the permission
	 * @param children children this permission includes
	 * @throws IllegalArgumentException If the name is invalid or already exists
	 *
	 * @see #Permission(String, String, boolean, Map)
	 */
	public Permission(String name, Map<String, Boolean> children) {
		this(name, null, Permission.DEFAULT_PERMISSION, children);
	}

	/**
	 * <p>
	 * Constructs a new permission with a name and description. The default
	 * value defaults to the default for permissions. The children list defaults
	 * to empty.<br>
	 * </p>
	 * <p>
	 * The name is a string that identifies the permission, and is the same
	 * string as would be found in permission yaml files. <br>
	 * Example: "entity.movement"
	 * </p>
	 * <p>
	 * The description is a short description of what the permission is for.
	 * <br>
	 * Example: "This allows entities to move around the world"
	 * </p>
	 *
	 * @param name The name of the permission
	 * @param description A short description of the permissions purpose
	 * @throws IllegalArgumentException If the name is invalid or already exists
	 *
	 * @see #Permission(String, String, boolean, Map)
	 */
	public Permission(String name, String description) {
		this(name, description, Permission.DEFAULT_PERMISSION, null);
	}

	/**
	 * <p>
	 * Constructs a new permission with a name, description, and default value.
	 * The children list defaults to empty.<br>
	 * </p>
	 * <p>
	 * The name is a string that identifies the permission, and is the same
	 * string as would be found in permission yaml files. <br>
	 * Example: "entity.movement"
	 * </p>
	 * <p>
	 * The description is a short description of what the permission is for.
	 * <br>
	 * Example: "This allows entities to move around the world"
	 * </p>
	 * <p>
	 * The default value is a value that determines the default for this
	 * permission. <br>
	 * Example: "false"
	 * </p>
	 *
	 * @param name The name of the permission
	 * @param description A short description of the permissions purpose
	 * @param defaultValue the default value for the permission
	 * @throws IllegalArgumentException If the name is invalid or already exists
	 *
	 * @see #Permission(String, String, boolean, Map)
	 */
	public Permission(String name, String description, boolean defaultValue) {
		this(name, description, defaultValue, null);
	}

	/**
	 * <p>
	 * Constructs a new permission with a name, description, default value, and
	 * children list.<br>
	 * </p>
	 * <p>
	 * The name is a string that identifies the permission, and is the same
	 * string as would be found in permission yaml files. <br>
	 * Example: "entity.movement"
	 * </p>
	 * <p>
	 * The description is a short description of what the permission is for.
	 * <br>
	 * Example: "This allows entities to move around the world"
	 * </p>
	 * <p>
	 * The default value is a value that determines the default for this
	 * permission. <br>
	 * Example: "false"
	 * </p>
	 * <p>
	 * The children is a list of permissions that are granted or revoked when
	 * this permission is granted/revoked. Each child permission has a boolean
	 * assigned to it. If the boolean is true, the child permission will inherit
	 * this permissions value. If it is false, it will inherit the inverse of
	 * this permissions value. <br>
	 * Example: "entity.jump" is mapped to "true"
	 * </p>
	 *
	 * @param name The name of the permission
	 * @param description A short description of the permissions purpose
	 * @param defaultValue the default value for the permission
	 * @param children children this permission includes
	 * @throws IllegalArgumentException If the name is invalid or already exists
	 */
	public Permission(String name, String description, boolean defaultValue,
		Map<String, Boolean> children) {
		if (!Permission.isValidName(name)) {
			throw new IllegalArgumentException(
				"Name '" + name + "' is invalid.");
		}
		if (Permission.exists(name)) {
			throw new IllegalArgumentException(
				"Permission '" + name + "' already exists.");

		}
		this.name = name;
		if (description == null) {
			this.description = "";
		}
		else {
			this.description = description.isEmpty() ? "" : description;
		}
		this.defaultValue = defaultValue;
		this.childPermissions =
			children == null ? new LinkedHashMap<>() : children;
		Permission.permissionByName.put(name, this);
	}

	/**
	 * <p>
	 * Constructs a new permission with a name, description, and children list.
	 * The default value defaults to the default for permissions.<br>
	 * </p>
	 * <p>
	 * The name is a string that identifies the permission, and is the same
	 * string as would be found in permission yaml files. <br>
	 * Example: "entity.movement"
	 * </p>
	 * <p>
	 * The description is a short description of what the permission is for.
	 * <br>
	 * Example: "This allows entities to move around the world"
	 * </p>
	 * <p>
	 * The children is a list of permissions that are granted or revoked when
	 * this permission is granted/revoked. Each child permission has a boolean
	 * assigned to it. If the boolean is true, the child permission will inherit
	 * this permissions value. If it is false, it will inherit the inverse of
	 * this permissions value. <br>
	 * Example: "entity.jump" is mapped to "true"
	 * </p>
	 *
	 * @param name The name of the permission
	 * @param description A short description of the permissions purpose
	 * @param children children this permission includes
	 * @throws IllegalArgumentException If the name is invalid or already exists
	 *
	 * @see #Permission(String, String, boolean, Map)
	 */
	public Permission(String name, String description,
		Map<String, Boolean> children) {
		this(name, description, Permission.DEFAULT_PERMISSION, children);
	}

	/**
	 * Returns true if the permission or one of the subpermissions contains the
	 * given permission. If the permission is the same as this one, it returns
	 * true as well.
	 *
	 * @param other the permission to search for
	 * @return true if this permission contains or equals the given permission
	 */
	public boolean contains(Permission other) {
		if (this.equals(other)) {
			return true;
		}
		return this.getAllSubpermissions().containsKey(other.getName());
	}

	/**
	 * Returns a complete list of permissions this permission grants or revokes.
	 * It recursively finds all sub-permissions, with parents overriding child
	 * permissions.
	 *
	 * @return the full list of child permissions
	 * @see #getChildPermissions()
	 */
	public Map<String, Boolean> getAllSubpermissions() {
		if (this.childrenCalculated) {
			return this.fullChildMap;
		}
		Map<String, Boolean> perms = this.getChildPermissions();
		for (String s : perms.keySet()) {
			if (!Permission.exists(s)) {
				Permission.log.warning(SafeResourceLoader
					.getString("NON_EXISTANT_SUBPERMISSON",
						Permission.RESOURCE_LOCATION)
					.replaceFirst("\\$PERMISSION", s));
				continue;// it was not created somehow
			}
			// this is recursive
			Optional<Permission> possiblePermission = Permission.getByName(s);
			possiblePermission.ifPresent(value -> {
				Map<String, Boolean> submap = value.getAllSubpermissions();
				for (Map.Entry<String, Boolean> entry : submap.entrySet()) {
					perms.computeIfAbsent(entry.getKey(),
						ignored -> entry.getValue());
				}
			});
		}
		this.fullChildMap = new HashMap<>();
		this.fullChildMap.putAll(perms);
		this.childrenCalculated = true;
		return this.fullChildMap;
	}

	/**
	 * Returns the default for this permission. <br>
	 * Example: "false"
	 *
	 * @return default value of this permission.
	 */
	public boolean getDefault() {
		return this.defaultValue;
	}

	/**
	 * Sets the default value of this permission.
	 * <p>
	 * This will not be saved to disk, and is a temporary operation until the
	 * server reloads permissions. Changing this default will cause all
	 * {@link PermissionHolder}s that contain this permission to recalculate
	 * their permissions
	 *
	 * @param value The new default to set
	 */
	public void setDefault(DefaultPermissionValue value) {
		this.defaultValue = value.value();
	}
}
