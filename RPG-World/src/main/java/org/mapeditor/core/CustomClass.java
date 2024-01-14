package org.mapeditor.core;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A user-defined class.
 *
 * @author Ches Burks
 *
 */
@Getter
@Setter
@NoArgsConstructor
public class CustomClass {
	/**
	 * A value in the custom class.
	 *
	 * @author Ches Burks
	 */
	@Getter
	@Setter
	@NoArgsConstructor
	public static class Member {
		/**
		 * Utility method to convert a string to a color.
		 *
		 * @param color The color, in #aarrggbb format that Tiled defaults to.
		 * @return The color object.
		 */
		public static Color stringToColor(@NonNull String color) {
			int a = Integer.parseInt(color.substring(1, 3), 16);
			int r = Integer.parseInt(color.substring(3, 5), 16);
			int g = Integer.parseInt(color.substring(5, 7), 16);
			int b = Integer.parseInt(color.substring(7, 9), 16);
			return new Color(r, g, b, a);
		}

		/**
		 * The name of the member.
		 */
		@NonNull
		private String name;
		/**
		 * The type, as defined in the tiled editor. This would either be
		 * <ul>
		 * <li>bool</li>
		 * <li>color</li>
		 * <li>float</li>
		 * <li>file</li>
		 * <li>int</li>
		 * <li>object</li>
		 * <li>string</li>
		 * <li>enum</li>
		 * <li>class</li>
		 * </ul>
		 */
		@NonNull
		private String type;
		/**
		 * The value of the entry. This will be a string form for most entries,
		 * however it will the propertytype for classes, so that we know what
		 * kind of class is stored.
		 */
		@NonNull
		private String value;
		/**
		 * The enum type, which only exists if this is an enum.
		 */
		private String enumType;

		/**
		 * The child contents, which only exists if this is a class.
		 */
		private CustomClass child;

		/**
		 * Create a new member.
		 *
		 * @param name The name of the member.
		 * @param type The type of element.
		 * @param value The value of the element. Should be set to the
		 *            propretyType if an enum/class, as we will use more
		 *            concrete values in that case.
		 */
		public Member(@NonNull String name, @NonNull String type,
			@NonNull String value) {
			this.name = name;
			this.type = type;
			this.value = value;
			this.enumType = null;
		}

		/**
		 * Create a new member.
		 *
		 * @param name The name of the member.
		 * @param type The type of element.
		 * @param value The value of the element. Should be set to the
		 *            propretyType if an enum/class, as we will use more
		 *            concrete values in that case.
		 * @param propertyType The enum type.
		 */
		public Member(@NonNull String name, @NonNull String type,
			@NonNull String value, @NonNull String propertyType) {
			this.name = name;
			this.type = type;
			this.value = value;
			this.enumType = propertyType;
		}

		/**
		 * Fetch the value as a boolean. Check the type before doing this.
		 *
		 * @return The value as a boolean.
		 */
		public boolean toBoolean() {
			return Boolean.parseBoolean(this.value);
		}

		/**
		 * Fetch the value as a color. Check the type before doing this.
		 *
		 * @return The value as a color.
		 */
		public Color toColor() {
			return Member.stringToColor(this.value);
		}

		/**
		 * Fetch the value as a float. Check the type before doing this.
		 *
		 * @return The value as a float.
		 */
		public float toFloat() {
			return Float.parseFloat(this.value);
		}

		/**
		 * Fetch the value as an int. Check the type before doing this.
		 *
		 * @return The value as an int.
		 */
		public int toInt() {
			return Integer.parseInt(this.value);
		}
	}

	@NonNull
	private String name;
	private List<Member> members;

	/**
	 * Create a new custom class.
	 *
	 * @param name The name of the property.
	 */
	public CustomClass(@NonNull String name) {
		this.name = name;
		this.members = new ArrayList<>();
	}

	/**
	 * Add a new data entry for this property.
	 *
	 * @param member The member to add.
	 */
	public void addMember(Member member) {
		this.members.add(member);
	}

	/**
	 * Fetch the value as a boolean. Check the type before doing this.
	 *
	 * @param memberName The name of the member.
	 * @return The value as a boolean.
	 */
	public boolean getBoolean(@NonNull String memberName) {
		Optional<Member> member = this.getMember(memberName);
		if (member.isEmpty()) {
			return false;
		}
		return member.get().toBoolean();
	}

	/**
	 * Fetch the value as a color. Check the type before doing this.
	 *
	 * @param memberName The name of the member.
	 * @return The value as a color.
	 */
	public Color getColor(@NonNull String memberName) {
		Optional<Member> member = this.getMember(memberName);
		if (member.isEmpty()) {
			return new Color(0, 0, 0);
		}
		return member.get().toColor();
	}

	/**
	 * Fetch a member as a float.
	 *
	 * @param memberName The name of the member.
	 * @return The float or a default value.
	 */
	public float getFloat(@NonNull String memberName) {
		Optional<Member> member = this.getMember(memberName);
		if (member.isEmpty()) {
			return 1f;
		}
		return member.get().toFloat();
	}

	/**
	 * Fetch the value as an int. Check the type before doing this.
	 *
	 * @param memberName The name of the member.
	 * @return The value as an int.
	 */
	public int getInt(@NonNull String memberName) {
		Optional<Member> member = this.getMember(memberName);
		if (member.isEmpty()) {
			return 0;
		}
		return member.get().toInt();
	}

	/**
	 * Find an immediate child member with the given name.
	 *
	 * @param memberName The name of the member.
	 * @return An optional that is either empty or contains the first child we
	 *         could find with that name.
	 */
	public Optional<Member> getMember(@NonNull String memberName) {
		return this.members.stream()
			.filter(member -> memberName.equals(member.getName())).findFirst();
	}

	@Override
	public String toString() {
		Gson gson = new Gson();
		return gson.toJson(this);
	}
}
