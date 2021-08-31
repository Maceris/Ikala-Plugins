# Entity Component System

A library that provides a framework for the Entity-Component-System pattern, to be extended by other plugins.

## Entity

An entity is a general purpose object, which stores components that make up its state. 
In reality this is just a unique identifier, but conceptually it's the idea of something that systems interact with.

## Component

A component stores raw data, or state. Each component contains information for a particular aspect of the entity.

Some examples of things that components might store:

* Health, stamina, or other resources
* Inventory for storing items
* World coordinates for physics simulations
* Graphics information for rendering the entity

## System

Systems perform actions on entities that have relevant components. They could be independent services or 
stages of processing in the core game loop.

Some example systems might be:

* Physics, which moves entities around based on simulations
* Ability system, which triggers magic spells to be cast based on inputs
* Graphics, which draws things in 3D space
