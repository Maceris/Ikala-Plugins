# Language

## Tags

Tags are structured in a simple YAML file format. Each tag is an entry in the YAML, with 
children being listed under their parent tag. Let's use an example excerpt to explain the structure.

```
beverage:
cold:
  frozen:
container:
  solid_container:
    bullet_container: # Exclusively stores bullets
  liquid_container:
  gas_container:
```

In this case, `beverage`, `cold`, and `container` are root tags with no parent.
`frozen` is a tag that has a parent of `cold`.

`solid_container`, `liquid_container`, and `gas_container` are all children of `container`, and `bullet_container` has 
`solid_container` as a parent. So we could say that a `bullet_container` *is a* `solid_container`, and so in turn, *is a* `container`.

There are no additional values in the structure, though you may add comments on any line to explain the use case or meaning if not obvious.

## Grammar

### Materials

Materials represent the substance something is made of. More technically, they are a collection of tags. Materials are structured in a YAML file format.

Each material has the following entries

* `tags` - a list of tags that apply to the material
* `children` (optional) - A list of tags that apply to the material

Let's use an example excerpt to explain the structure.

```
metal:
  tags:
    - 'conductive'
    - 'metal'
  children:
    steel:
      tags:
        - 'steel'
    gold:
      tags:
        - 'gold'
```

In this example, `metal` is a root material with no parent. It indicates metals are `conductive`, and `metal`.

There are two children, `steel` and `gold`, each that adds their own tags.

Let's say we had a structure like this among the noun tags:

```
conductive:
solid:
  metal:
    copper:
    gold:
    steel:
```

In that case, the `steel` material would end up with the tags `conductive` from its parent, and `steel` from its definition.
It would not have `metal` or `solid`, since `steel` already inherits from these according to the tag structure.

### Nouns

Nouns are structured in a YAML file format. There is no nesting of the structure, all nouns are root level entries.

Nouns inherit all the tags from the material and tags, with duplicate tags removed like in materials.

They have a couple of entries, at least one of which should be filled out.

* `material` - The material that the noun is primarily made of
* `tags` - A list of tags that apply to the noun

Here is an example structure.

```
burger:
  tags:
    - 'food'
gun:
  material: 'steel'
  tags:
    - bullet_container
sword:
  material: 'steel'
```

### Verbs

Verbs are structured in a YAML file format.
