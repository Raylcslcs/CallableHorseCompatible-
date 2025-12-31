package tschipp.callablehorses.mixin;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CallableHorsesMixinPluginTest
{
	@Test
	public void shouldNotRequireLoadingCompatTargetClass() throws Exception
	{
		URL mainClasses = new File("build/classes/java/main/").toURI().toURL();
		URL testClasses = new File("build/classes/java/test/").toURI().toURL();

		try (DenyingLoader loader = new DenyingLoader(new URL[] { mainClasses, testClasses }, CallableHorsesMixinPluginTest.class.getClassLoader()))
		{
			Class<?> pluginClass = Class.forName("tschipp.callablehorses.mixin.CallableHorsesMixinPlugin", true, loader);
			Object plugin = pluginClass.getDeclaredConstructor().newInstance();

			Method shouldApplyMixin = pluginClass.getMethod("shouldApplyMixin", String.class, String.class);
			boolean result = (boolean) shouldApplyMixin.invoke(plugin, "some.Target", "tschipp.callablehorses.mixin.compat.iceandfire.EntityDragonBaseMixin");

			assertTrue(result, "Mixin plugin should apply compat mixin when the target class exists on the classpath even if loading is denied.");
		}
	}

	private static class DenyingLoader extends URLClassLoader
	{
		private static final String DENIED = "com.github.alexthe666.iceandfire.entity.EntityDragonBase";

		private DenyingLoader(URL[] urls, ClassLoader parent)
		{
			super(urls, parent);
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
		{
			if (DENIED.equals(name))
				throw new ClassNotFoundException(name + " is denied by test loader");

			if (name.startsWith("tschipp.callablehorses."))
			{
				Class<?> loaded = findLoadedClass(name);
				if (loaded != null)
					return loaded;

				try
				{
					Class<?> found = findClass(name);
					if (resolve)
						resolveClass(found);
					return found;
				}
				catch (ClassNotFoundException ignored)
				{
				}
			}

			return super.loadClass(name, resolve);
		}
	}
}

