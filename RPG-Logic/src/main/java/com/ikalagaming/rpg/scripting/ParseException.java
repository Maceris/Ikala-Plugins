package com.ikalagaming.rpg.scripting;

/**
 * An exception that occurred during parsing.
 * 
 * @author Ches Burks
 *
 */
public class ParseException extends RuntimeException {

	/**
	 * The serial version ID.
	 */
	private static final long serialVersionUID = -7914474355664999029L;

	/**
	 * Constructs a new exception with {@code null} as its detail message. The
	 * cause is not initialized, and may subsequently be initialized by a call
	 * to {@link #initCause}.
	 */
	public ParseException() {
		super();
	}

	/**
	 * Constructs a new exception with the specified detail message. The cause
	 * is not initialized, and may subsequently be initialized by a call to
	 * {@link #initCause}.
	 *
	 * @param message the detail message. The detail message is saved for later
	 *            retrieval by the {@link #getMessage()} method.
	 */
	public ParseException(String message) {
		super(message);
	}

	/**
	 * Constructs a new exception with the specified detail message and cause.
	 * <p>
	 * Note that the detail message associated with {@code cause} is <i>not</i>
	 * automatically incorporated in this exception's detail message.
	 *
	 * @param message the detail message (which is saved for later retrieval by
	 *            the {@link #getMessage()} method).
	 * @param cause the cause (which is saved for later retrieval by the
	 *            {@link #getCause()} method). (A <code>null</code> value is
	 *            permitted, and indicates that the cause is nonexistent or
	 *            unknown.)
	 * @since 1.4
	 */
	public ParseException(String message, Throwable cause) {
		super(message, cause);
	}

}
