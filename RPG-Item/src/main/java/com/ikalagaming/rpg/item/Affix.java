package com.ikalagaming.rpg.item;

import com.ikalagaming.rpg.item.enums.AffixType;
import com.ikalagaming.rpg.item.enums.Quality;
import com.ikalagaming.rpg.item.persistence.ItemCriteriaConverter;
import com.ikalagaming.rpg.item.persistence.ItemStatsConverter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * A modifier for a weapon that grants stats and is shown as a prefix or suffix.
 *
 * @author Ches Burks
 */
@Getter
@Setter
@EqualsAndHashCode
@Entity
@Table(name = Affix.TABLE_NAME)
public class Affix {
    /** The name of the table in the database. */
    static final String TABLE_NAME = "AFFIX";

    /**
     * The unique name of the affix, which should follow a standard format and be human readable but
     * unlocalized.
     *
     * @param ID The name of the affix in the database/code level.
     * @return The name of the affix in the database/code level.
     */
    @Id
    @Column(name = "ID")
    private String ID;

    /**
     * The minimum item level requirement for this item.
     *
     * @param levelRequirement The item level requirement for this item.
     * @return The item level requirement for this item.
     */
    @Column(name = "LEVEL_REQUIREMENT")
    private Integer levelRequirement;

    /**
     * What kind of affix this is, like a prefix or suffix.
     *
     * @param affixType The type of affix.
     * @return The type of affix.
     */
    @Column(name = "AFFIX_TYPE")
    @Enumerated(EnumType.STRING)
    private AffixType affixType;

    /**
     * The types of items this affix can be applied to.
     *
     * @param itemCriteria The types of items this affix can be applied to.
     * @return The types of items this affix can be applied to.
     */
    @Column(name = "ITEM_CRITERIA")
    @Convert(converter = ItemCriteriaConverter.class)
    private ItemCriteria itemCriteria = new ItemCriteria();

    /**
     * The minimum quality of items this can appear on.
     *
     * @param quality The minimum quality of items this can appear on.
     * @return The minimum quality of items this can appear on.
     */
    @Column(name = "QUALITY")
    @Enumerated(EnumType.STRING)
    private Quality quality;

    /**
     * Stat bonuses provided by the affix.
     *
     * @param itemStats The stat bonuses provided by the affix.
     * @return The stat bonuses provided by the affix.
     */
    @Column(name = "ITEM_STATS")
    @Convert(converter = ItemStatsConverter.class)
    private ItemStats itemStats = new ItemStats();
}
