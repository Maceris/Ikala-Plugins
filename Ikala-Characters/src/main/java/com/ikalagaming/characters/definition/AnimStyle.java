package com.ikalagaming.characters.definition;

import com.ikalagaming.characters.data.Doc;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonPath;
import com.ikalagaming.characters.data.Optional;

import java.util.List;
import java.util.Map;

/**
 * A way of moving that overrides some of a plan's actions, like {@code zombie} (shambling and
 * lurching), {@code hunched} or a weapon's stance. A design picks one; actions it leaves out come
 * from the plan's {@link AnimationSet animation sets}.
 *
 * @param id This style's ID.
 * @param plan The body plan it is for.
 * @param actions The actions it overrides.
 * @param tags Tags editors filter by.
 * @author Ches Burks
 */
@Doc(
        "A way of moving that overrides some of a plan's actions, like a zombie's shamble or a"
                + " weapon's stance. Actions it leaves out come from the plan's animation sets.")
public record AnimStyle(
        @Doc("This style's ID, like \"ikala:style/zombie\".") Id id,
        @Doc("The body plan it is for.") Id plan,
        @Doc("The actions it overrides, like {\"move\": {...}}.")
                Map<String, AnimationSet.Action> actions,
        @Doc("Tags that editors filter by, like \"weapon\".") @Optional List<String> tags)
        implements Definition {

    /** The field for the plan. */
    public static final String FIELD_PLAN = "plan";

    /** The field for the actions. */
    public static final String FIELD_ACTIONS = "actions";

    @Override
    public void check(Checks checks) {
        BodyPlan body = checks.require(BodyPlan.class, plan, JsonPath.at(FIELD_PLAN));
        AnimationSet.checkActions(actions, body, checks, JsonPath.at(FIELD_ACTIONS));
    }
}
