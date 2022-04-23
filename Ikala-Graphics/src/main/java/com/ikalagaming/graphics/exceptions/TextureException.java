package com.ikalagaming.graphics.exceptions;

/**
 * There was an exception with creating a texture.
 *
 * @author Ches Burks
 *
 */
public class TextureException extends RuntimeException {

	/**
	 * Generated ID.
	 */
	private static final long serialVersionUID = 188700139445819342L;

	/**
	 * Create an exception without any details.
	 */
	public TextureException() {
		super();
	}

	/**
	 * Constructs a new runtime exception with the specified detail message.The
	 * cause is not initialized, and may subsequently be initialized by a call
	 * to {@link #initCause(Throwable) initCause}.
	 *
	 * @param message The detail message. The detail message is saved for later
	 *            retrieval by the {@link #getMessage()} method.
	 */
	public TextureException(String message) {
		super(message);
	}

	/**
	 * Constructs a new runtime exception with the specified detail message and
	 * cause.
	 *
	 * Note that the detail message associated with cause is not automatically
	 * incorporated in this runtime exception's detail message.
	 *
	 * @param message The detail message (which is saved for later retrieval by
	 *            the {@link #getMessage()} method).
	 * @param cause The cause (which is saved for later retrieval by the
	 *            {@link #getCause()} method). (A null value is permitted, and
	 *            indicates that the cause is nonexistent or unknown.)
	 */
	public TextureException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructs a new runtime exception with the specified detailmessage,
	 * cause, suppression enabled or disabled, and writablestack trace enabled
	 * or disabled.
	 *
	 * @param message The detail message.
	 * @param cause The cause. (A null value is permitted,and indicates that the
	 *            cause is nonexistent or unknown.)
	 * @param enableSuppression Whether or not suppression is enabledor
	 *            disabled.
	 * @param writableStackTrace Whether or not the stack trace shouldbe
	 *            writable.
	 */
	public TextureException(String message, Throwable cause,
		boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * Constructs a new runtime exception with the specified cause and a detail
	 * message of {@code (cause==null ? null : cause.toString())} (which
	 * typically contains the class and detail message of cause). This
	 * constructor is useful for runtime exceptions that are little more than
	 * wrappers for other throwables.
	 *
	 * @param cause The cause (which is saved for later retrieval by the
	 *            {@link #getCause()} method). (A null value is permitted, and
	 *            indicates that the cause is nonexistent or unknown.)
	 */
	public TextureException(Throwable cause) {
		super(cause);
	}

}
