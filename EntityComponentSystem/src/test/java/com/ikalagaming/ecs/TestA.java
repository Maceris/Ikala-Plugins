package com.ikalagaming.ecs;

import lombok.Getter;
import lombok.Setter;

/**
 * A test component.
 *
 * @author Ches Burks
 */
public class TestA extends Component<TestA> {

    @Getter @Setter private int testInt;
}
