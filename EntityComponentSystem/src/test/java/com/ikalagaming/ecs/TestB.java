package com.ikalagaming.ecs;

import lombok.Getter;
import lombok.Setter;

/**
 * A test component.
 *
 * @author Ches Burks
 */
public class TestB extends Component<TestB> {

    @Getter @Setter private String testString;
}
