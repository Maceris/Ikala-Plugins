package com.ikalagaming.item;

import com.ikalagaming.database.Database;
import com.ikalagaming.database.DatabasePlugin;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.plugins.Plugin;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.io.File;
import java.util.Arrays;
import java.util.Properties;

/**
 * Handles the lifecycle for the item plugin.
 * 
 * @author Ches Burks
 *
 */
public class ItemPlugin extends Plugin {

	@Override
	public boolean onEnable() {
		File db = PluginFolder.getResource(DatabasePlugin.PLUGIN_NAME,
			ResourceType.DATA, "mainDatabase");
		Database database = new Database(db.getAbsolutePath());

		Properties prop = new Properties();
		prop.setProperty("hibernate.connection.url",
			database.getConnectionString());
		prop.setProperty("dialect", "org.hibernate.dialect.H2Dialect");
		prop.setProperty("hibernate.connection.username", "sa");
		prop.setProperty("hibernate.connection.password", "");
		prop.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
		prop.setProperty("hibernate.hbm2ddl.auto", "create");
		prop.setProperty("show_sql", "true");
		prop.setProperty("hibernate.transaction.jta.platform",
			"org.hibernate.service.jta.platform.internal.BitronixJtaPlatform");

		Configuration config = new Configuration().addProperties(prop);
		Arrays
			.asList(Item.class, AttributeBoolean.class, AttributeFloat.class,
				AttributeInteger.class, AttributeString.class)
			.forEach(config::addAnnotatedClass);

		SessionFactory sessionFactory = config.buildSessionFactory();
		Session session = sessionFactory.openSession();

		Item item = new Item();
		AttributeBoolean bool = new AttributeBoolean();
		bool.setName("testBool");
		bool.setValue(true);
		AttributeFloat floatval = new AttributeFloat();
		floatval.setName("testFloat");
		floatval.setValue(1.23d);
		AttributeInteger intval = new AttributeInteger();
		intval.setName("testInt");
		intval.setValue(3);
		AttributeString str = new AttributeString();
		str.setName("testStr");
		str.setValue("sample");

		item.getAttributes().add(bool);
		item.getAttributes().add(floatval);
		item.getAttributes().add(intval);
		item.getAttributes().add(str);
		item.getTags().add("tag1");
		item.getTags().add("tag2");

		session.beginTransaction();
		session.save(item);
		session.getTransaction().commit();
		session.close();

		return true;
	}
}
