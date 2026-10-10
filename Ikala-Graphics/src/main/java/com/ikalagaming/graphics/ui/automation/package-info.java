/**
 * Drives the retained UI like a player would, for tests and scripted checks.
 *
 * <p>{@link com.ikalagaming.graphics.ui.automation.Steps} find nodes by {@link
 * com.ikalagaming.graphics.ui.automation.Selector}, such as {@code about//close} or {@code
 * about//text=Close}, then click them, type into them, wait for them or check their text. Clicks
 * and typing are real IkGui input aimed at the node's rectangle, so a covered or scrolled away node
 * fails the way it would for a player. Plugins start runs with {@code GraphicsContext.ui()
 * .automate(...)}, scripts with {@code ui.test()}, and tests drive frames themselves with {@link
 * com.ikalagaming.graphics.ui.automation.UiTestDriver}.
 */
package com.ikalagaming.graphics.ui.automation;
