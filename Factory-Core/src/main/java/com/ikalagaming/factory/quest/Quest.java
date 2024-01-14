package com.ikalagaming.factory.quest;

public record Quest(String name, String description, boolean main, String tab,
	Prerequisites prerequisites, boolean repeatable, boolean autoClaim) {

}
