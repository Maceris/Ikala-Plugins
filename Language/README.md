# Language

## Files

There are several data files that need to be set up. Details about the contents are detailed elsewhere in this document, but here we will list out the files that will be required, located in the data folder for this plugin.

When we say &lt;locale&gt; below, we mean a code that represents a language and country. The format would be a lowercase (ISO 639) language code, an underscore, then an uppercase (ISO 3166) country code. For example, `en_US`, or `fr_CA`. We look for a country code and language first, but if we can't find it, we will try to check for just the language like `en`.

* `tags.yml` - Stores tags used to encode information about nouns
* `tags_&lt;locale&gt;.txt` - Used to localize the tags
* `materials.yml` - Specifies materials, used to group tags together
* `nouns.yml` - Specifies what nouns exist in the game
* `nouns_&lt;locale&gt;.txt` - Used to localize nouns
* `verbs.yml` - Specifies which verbs are possible in the game
* `verbs_&lt;locale&gt;.txt` - Used to localize verbs
* `properties.yml` - Specifies properties that entities can have
* `properties_&lt;locale&gt;.txt` - Used to localize properties
* `grammar_&lt;locale&gt;.txt` - Specifies the grammar for this language
* `pronouns_&lt;locale&gt;.txt` - Specifies the pronouns for this language
* `descriptors_&lt;locale&gt;.txt` - Specifies descriptors for this language

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


### Pronouns

Pronouns are structured in a YAML file format.

We identify pronouns by Gender, Number, and Animation (referred to as GNA). The available grammatical genders are masculine, feminine, and neuter. Numbers are singular, and plural. Animations are animate and inanimate. This may not cover every language, but should be enough for the majority of common languages.

The file is listed by pronoun, containing the following elements:

* `GNAs` -  These are either
    * A list of three letter representations of GNA in that order, like `asn` for animate, singular, and neuter or `ipm` for inanimate, plural, and masculine. 
    * The value `all`, indicating any combination of gender, number, and animation are valid.

Example:

```
it:
  GNAs:
    - ism
    - isf
    - isn
him:
  GNAs:
    - asm
her:
  GNAs:
    - asf
them:
  GNAs:
    - asn
    - apm
    - apf
    - apn
    - ipm
    - ipf
    - ipn

```

### Descriptors

Descriptors are used as determiners or articles, occurring with nouns and noun phrases to indicate ownership or reference.

* `GNAs` -  These are either
    * A list of three letter representations of GNA in that order, like `asn` for animate, singular, and neuter or `ipm` for inanimate, plural, and masculine. 
    * The value `all`, indicating any combination of gender, number, and animation are valid.
* `type` - One of
    * `possessive` - used for possessive pronouns
    * `definite` - used for definite articles
    * `indefinite` - Used for indefinite articles
* `owner` - The owner of the object this refers to, one of:
    * `player` - Owned by the player
    * `non_player` - Not owned by the player, but still possessive
    * Any pronoun, such as `him` - Owned by the target of said pronoun
    * `null` - Not relevant, usually articles

Example:

```
my:
  GNAs: all
  type: possessive
   owner: player
these:
  GNAs:
    - apm
    - apf
    - apn
    - ipm
    - ipf
    - ipn
  type: possessive
  owner: player
that:
  GNAs: all
  type: possessive
  owner: non_player
his:
  GNAs: all
  type: possessive
  owner: him
her:
  GNAs: all
  type: possessive
  owner: her
the:
  GNAs: all
  type: definite
  owner: null
a:
  GNAs:
    - asm
    - asf
    - asn
    - ism
    - isf
    - isn
  type: indefinite
  owner: null
```
