package com.ikalagaming.item;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.AnyMetaDef;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.ManyToAny;
import org.hibernate.annotations.MetaValue;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;

/**
 * An item in the game.
 * 
 * @author Ches Burks
 *
 */
@Entity
@NoArgsConstructor
@Getter
@Setter
public class Item {

	@Id
	@GeneratedValue
	@Column(name = "item_id")
	private Integer id;

	@ManyToAny(metaDef = "AttributeMetaDef",
		metaColumn = @Column(name = "attribute_type"))
	@AnyMetaDef(idType = "integer", metaType = "string",
		metaValues = {
			@MetaValue(value = "B", targetEntity = AttributeBoolean.class),
			@MetaValue(value = "F", targetEntity = AttributeFloat.class),
			@MetaValue(value = "I", targetEntity = AttributeInteger.class),
			@MetaValue(value = "S", targetEntity = AttributeString.class)})
	@JoinTable(name = "repository_attributes",
		joinColumns = @JoinColumn(name = "repository_id"),
		inverseJoinColumns = @JoinColumn(name = "attribute_id"))
	@Cascade(CascadeType.ALL)
	private List<Attribute<?>> attributes = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name="item_tags", joinColumns=@JoinColumn(name="item_id"))
	@Column(name="tags")
	private List<String> tags = new ArrayList<>();

}
